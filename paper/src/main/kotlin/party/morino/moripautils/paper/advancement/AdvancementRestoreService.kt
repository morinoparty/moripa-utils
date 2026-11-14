/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.advancement

import org.bukkit.Bukkit
import party.morino.moripautils.paper.advancement.husksync.HuskSyncAdvancementGateway
import party.morino.moripautils.paper.model.advancement.AdvancementRestoreResult
import party.morino.moripautils.paper.model.advancement.AdvancementSnapshotListing
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * HuskSync のスナップショットから進捗だけを復元するサービス
 *
 * Tab 補完はキー入力のたびに呼ばれるため、スナップショットの一覧を実行者と対象のプレイヤーの組ごとに短時間キャッシュする。
 * 番号での指定は、実行者が補完で見た一覧 (キャッシュ) を基準に解決し、補完から実行までの間に番号がずれないようにする。
 * HuskSync は任意依存のため、HuskSync の型はこのクラスに持ち込まず、[HuskSyncAdvancementGateway] を通して使う。
 *
 * @param clock キャッシュの有効期限の判定に使う時計
 * @param huskSyncEnabled HuskSync が有効かどうかを返す関数
 */
class AdvancementRestoreService(
    private val clock: Clock = Clock.systemUTC(),
    private val huskSyncEnabled: () -> Boolean = { Bukkit.getPluginManager().isPluginEnabled(HUSKSYNC_PLUGIN_NAME) },
) {
    /**
     * キャッシュした一覧
     *
     * @property listing スナップショットの一覧
     * @property fetchedAt 取得した日時
     */
    private data class CachedListing(val listing: AdvancementSnapshotListing, val fetchedAt: Instant)

    /**
     * キャッシュのキー
     *
     * @property viewer 実行者の名前
     * @property playerName 対象のプレイヤー名 (小文字)
     */
    private data class CacheKey(val viewer: String, val playerName: String)

    /** 実行者と対象のプレイヤーの組ごとのスナップショットの一覧 */
    private val cache = ConcurrentHashMap<CacheKey, CachedListing>()

    /** HuskSync が導入されていて有効かどうか */
    val isAvailable: Boolean
        get() = huskSyncEnabled()

    /**
     * Tab 補完用にスナップショットの一覧を取得する (直近に取得した一覧があればそれを使う)
     *
     * @param viewer 実行者の名前
     * @param playerName プレイヤー名
     * @return スナップショットの一覧、プレイヤーが記録されていない場合は null
     */
    suspend fun listingForSuggestions(viewer: String, playerName: String): AdvancementSnapshotListing? {
        val key = cacheKey(viewer, playerName)
        return cached(key, SUGGESTION_CACHE_TTL) ?: fetch(key)
    }

    /**
     * コマンドの実行用にスナップショットの一覧を取得する
     *
     * 番号での指定は補完で見た一覧を基準にするため、少し前に取得した一覧もそのまま使う。
     * ID での指定は最新の一覧から探す。
     *
     * @param viewer 実行者の名前
     * @param playerName プレイヤー名
     * @param selector スナップショットの指定
     * @return スナップショットの一覧、プレイヤーが記録されていない場合は null
     */
    suspend fun listingForExecution(
        viewer: String,
        playerName: String,
        selector: SnapshotSelector,
    ): AdvancementSnapshotListing? {
        val key = cacheKey(viewer, playerName)
        return when (selector) {
            is SnapshotSelector.ByIndex -> cached(key, EXECUTION_CACHE_TTL) ?: fetch(key)
            is SnapshotSelector.ById -> fetch(key)
        }
    }

    /**
     * スナップショットの進捗だけを、プレイヤーの現在のデータへ復元する
     *
     * @param listing 対象のプレイヤーのスナップショットの一覧
     * @param snapshotId 進捗を取り出すスナップショットの ID
     * @param mainContext サーバーのメインスレッドのコンテキスト
     * @return 復元の結果
     */
    suspend fun restore(
        listing: AdvancementSnapshotListing,
        snapshotId: UUID,
        mainContext: CoroutineContext,
    ): AdvancementRestoreResult {
        val result = HuskSyncAdvancementGateway.restoreAdvancements(listing.playerId, snapshotId, mainContext)
        // スナップショットが増えて番号がずれるため、誰の補完でも次は取り直す
        val playerName = listing.playerName.lowercase(Locale.ROOT)
        cache.keys.removeIf { it.playerName == playerName }
        return result
    }

    /**
     * キャッシュのキーを作る
     *
     * @param viewer 実行者の名前
     * @param playerName プレイヤー名
     * @return キャッシュのキー
     */
    private fun cacheKey(viewer: String, playerName: String) = CacheKey(viewer, playerName.lowercase(Locale.ROOT))

    /**
     * 有効期限内のキャッシュを返す
     *
     * @param key キャッシュのキー
     * @param ttl 有効期限
     * @return キャッシュした一覧、無いか期限切れの場合は null
     */
    private fun cached(key: CacheKey, ttl: Duration): AdvancementSnapshotListing? {
        val entry = cache[key] ?: return null
        return entry.listing.takeIf { Duration.between(entry.fetchedAt, clock.instant()) <= ttl }
    }

    /**
     * HuskSync から一覧を取得してキャッシュする
     *
     * @param key キャッシュのキー
     * @return スナップショットの一覧、プレイヤーが記録されていない場合は null
     */
    private suspend fun fetch(key: CacheKey): AdvancementSnapshotListing? {
        val listing = HuskSyncAdvancementGateway.listSnapshots(key.playerName) ?: return null
        cache[key] = CachedListing(listing, clock.instant())
        return listing
    }

    companion object {
        /** HuskSync のプラグイン名 */
        const val HUSKSYNC_PLUGIN_NAME: String = "HuskSync"

        /** Tab 補完で同じ一覧を使い回す時間 */
        private val SUGGESTION_CACHE_TTL: Duration = Duration.ofSeconds(10)

        /** 番号での指定を、補完で見た一覧から解決する時間 */
        private val EXECUTION_CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}
