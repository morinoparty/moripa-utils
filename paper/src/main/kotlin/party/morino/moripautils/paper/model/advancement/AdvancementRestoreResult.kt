/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.model.advancement

import java.util.UUID

/**
 * スナップショットから進捗を復元した結果
 */
sealed interface AdvancementRestoreResult {
    /**
     * 復元前のデータを固定したスナップショットとして保存し、進捗を差し替えたデータを保存して反映を HuskSync に依頼した
     *
     * @property backupId 復元前のデータを保存したスナップショットの ID
     * @property restoredId 進捗を差し替えたデータのスナップショットの ID
     * @property advancementCount 復元した進捗の数 (レシピの解除を除く)
     */
    data class Restored(val backupId: UUID, val restoredId: UUID, val advancementCount: Int) : AdvancementRestoreResult

    /** HuskSync にプレイヤーが記録されていない */
    data object PlayerNotFound : AdvancementRestoreResult

    /** 指定した ID のスナップショットが存在しない (ローテーションで削除された場合など) */
    data object SnapshotNotFound : AdvancementRestoreResult

    /** 指定したスナップショットに進捗のデータが含まれていない (HuskSync の advancements の同期が無効だった場合など) */
    data object NoAdvancementData : AdvancementRestoreResult

    /** プレイヤーの現在のデータを取得できなかった */
    data object NoCurrentData : AdvancementRestoreResult

    /** 復元前のデータを保存できなかった (DataSaveEvent がキャンセルされた場合や、時間内に保存が終わらなかった場合) */
    data object BackupFailed : AdvancementRestoreResult

    /**
     * 復元前のデータは保存したが、進捗を差し替えたデータを保存できなかった
     *
     * @property backupId 復元前のデータを保存したスナップショットの ID
     */
    data class RestoreFailed(val backupId: UUID) : AdvancementRestoreResult
}
