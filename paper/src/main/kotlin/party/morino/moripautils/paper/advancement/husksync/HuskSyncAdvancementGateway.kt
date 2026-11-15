/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.advancement.husksync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.william278.husksync.api.HuskSyncAPI
import net.william278.husksync.data.DataSnapshot
import net.william278.husksync.user.User
import party.morino.moripautils.paper.model.advancement.AdvancementRestoreResult
import party.morino.moripautils.paper.model.advancement.AdvancementSnapshotListing
import party.morino.moripautils.paper.model.advancement.AdvancementSnapshotSummary
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * HuskSync の API を呼び出す処理をまとめたもの
 *
 * HuskSync は任意依存のため、このオブジェクトは HuskSync が有効なことを確かめてから使うこと
 * (HuskSync が無い環境で読み込むと NoClassDefFoundError になる)。
 * HuskSync の型はこのパッケージの外へ出さない。
 *
 * HuskSync は desertwell などのライブラリを net.william278.husksync.libraries の下へ移して同梱している。
 * それらの型 (ThrowingConsumer など) を引数に取る API は、元のパッケージ名では解決できないため使わない。
 */
internal object HuskSyncAdvancementGateway {
    /** 復元前のデータを保存したスナップショットの保存理由 (HuskSync の上限の 32 文字以内) */
    private val BACKUP_SAVE_CAUSE: DataSnapshot.SaveCause = DataSnapshot.SaveCause.of("MORIPA_ADVANCEMENT_BACKUP")

    /** スナップショットの保存を待つ時間 (DataSaveEvent がキャンセルされると完了が通知されないため、上限を設ける) */
    private val SAVE_TIMEOUT = 10.seconds

    /** HuskSync の API (HuskSync の有効化前や無効化後は IllegalStateException になる) */
    private val api: HuskSyncAPI
        get() = HuskSyncAPI.getInstance()

    /**
     * プレイヤーのスナップショットを新しい順に取得する
     *
     * @param playerName プレイヤー名 (オフラインでもよい)
     * @return スナップショットの一覧、HuskSync にプレイヤーが記録されていない場合は null
     */
    suspend fun listSnapshots(playerName: String): AdvancementSnapshotListing? {
        val user = api.getUser(playerName).await().orElse(null) ?: return null
        // 取得順は保証されていないため、保存日時で並べ直す
        val snapshots = api.getSnapshots(user).await().sortedByDescending { it.timestamp }
        return AdvancementSnapshotListing(
            playerId = user.uuid,
            playerName = user.name,
            snapshots = snapshots.mapIndexed { index, snapshot ->
                AdvancementSnapshotSummary(
                    index = index + 1,
                    id = snapshot.id,
                    timestamp = snapshot.timestamp,
                    saveCause = snapshot.saveCause.name(),
                    pinned = snapshot.isPinned,
                    advancementCount = snapshot.advancements.orElse(null)?.completedExcludingRecipes?.size,
                )
            },
        )
    }

    /**
     * スナップショットの進捗だけを、プレイヤーの現在のデータへ復元する
     *
     * 1. 現在のデータを、固定したスナップショットとして保存する (/userdata restore で復元前に戻せるようにする)。
     *    固定しないと、HuskSync の backup_frequency によるローテーションで直後の保存に置き換えられることがある。
     * 2. 保存したデータを複製して進捗だけを差し替え、現在のデータとして保存する。
     *    差し替え後のデータは複製時の日時になるため、必ず手順 1 のスナップショットより新しくなり、最新のデータとして扱われる。
     * 3. HuskSync の /userdata restore と同じく、Redis に参加時用のデータが残っていれば差し替え、データの更新を通知する。
     *    オンラインのプレイヤーにはすぐに反映され、オフラインなら次回の参加時に反映される。
     *    (Redis のデータを差し替えないと、サーバーを移動した直後のプレイヤーが参加したときに古いデータで上書きされる)
     *
     * @param playerId プレイヤーの UUID
     * @param snapshotId 進捗を取り出すスナップショットの ID
     * @param mainContext サーバーのメインスレッドのコンテキスト
     * @return 復元の結果
     */
    suspend fun restoreAdvancements(
        playerId: UUID,
        snapshotId: UUID,
        mainContext: CoroutineContext,
    ): AdvancementRestoreResult {
        val user = api.getUser(playerId).await().orElse(null) ?: return AdvancementRestoreResult.PlayerNotFound
        val source = api.getSnapshot(user, snapshotId).await().firstOrNull()
            ?: return AdvancementRestoreResult.SnapshotNotFound
        val advancements = source.advancements.orElse(null) ?: return AdvancementRestoreResult.NoAdvancementData

        // このサーバーにいるプレイヤーの場合、HuskSync は呼び出した時点でプレイヤーの状態を読み取るため、メインスレッドで呼ぶ
        val currentFuture = withContext(mainContext) { api.getCurrentData(user) }
        val current = currentFuture.await().orElse(null) ?: return AdvancementRestoreResult.NoCurrentData

        current.id = UUID.randomUUID()
        current.isPinned = true
        current.saveCause = BACKUP_SAVE_CAUSE
        val backup = api.packSnapshot(current)
        if (!saveSnapshot(user, backup) { _, _ -> }) {
            return AdvancementRestoreResult.BackupFailed
        }

        // copy() は新しい ID と現在の日時を持つ複製を作る (展開しても ID と日時は変わらない)
        // editPackedSnapshot は使わない。引数の ThrowingConsumer (desertwell) は HuskSync の JAR では
        // net.william278.husksync.libraries.desertwell に移されているため、呼ぶと NoClassDefFoundError になる。
        val restoredData = api.unpackSnapshot(backup.copy())
        restoredData.isPinned = false
        restoredData.saveCause = DataSnapshot.SaveCause.BACKUP_RESTORE
        restoredData.setAdvancements(advancements)
        val restored = api.packSnapshot(restoredData)
        val redis = api.plugin.redisManager
        val saved = saveSnapshot(user, restored) { savedUser, savedSnapshot ->
            redis.getUserData(savedUser).ifPresent { redis.setUserData(savedUser, savedSnapshot) }
            redis.sendUserDataUpdate(savedUser, savedSnapshot)
        }
        if (!saved) {
            return AdvancementRestoreResult.RestoreFailed(backup.id)
        }

        return AdvancementRestoreResult.Restored(
            backupId = backup.id,
            restoredId = restored.id,
            advancementCount = advancements.completedExcludingRecipes.size,
        )
    }

    /**
     * スナップショットを保存し、保存が終わるまで待つ
     *
     * @param user 保存先のユーザー
     * @param snapshot 保存するスナップショット
     * @param after 保存した後に HuskSync のスレッドで行う処理
     * @return 時間内に保存できた場合は true
     */
    private suspend fun saveSnapshot(
        user: User,
        snapshot: DataSnapshot.Packed,
        after: (User, DataSnapshot.Packed) -> Unit,
    ): Boolean {
        val saved = CompletableDeferred<Unit>()
        // コールバックは DataSaveEvent がキャンセルされなかった場合だけ、保存後に呼ばれる
        api.addSnapshot(user, snapshot) { savedUser, savedSnapshot ->
            try {
                after(savedUser, savedSnapshot)
            } finally {
                saved.complete(Unit)
            }
        }
        return withTimeoutOrNull(SAVE_TIMEOUT) { saved.await() } != null
    }
}
