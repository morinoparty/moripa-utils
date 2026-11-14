/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.worldtree

import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.WorldTreeConfig
import party.morino.moripautils.common.model.config.WorldTreeSeason
import party.morino.moripautils.common.worldtree.WorldTreeSeasonResolver
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.model.worldtree.WorldTreeApplyResult
import party.morino.moripautils.paper.worldtree.worldedit.WorldEditWorldTreeEditor
import java.util.logging.Level

/**
 * 世界樹の季節を切り替える入口 (起動時の自動切り替えと /mu worldtree が共通で使う)
 *
 * world tree 機能が有効な場合だけ Koin に登録される。
 * WorldEdit の有無とワールドの存在を確認してから [WorldEditWorldTreeEditor] に置き換えを任せ、結果をログに残す。
 * 失敗してもプラグイン全体を止めないよう、例外はすべて結果に変換する。
 * ブロックを書き換えるため、すべてのメソッドはメインスレッドから呼ぶこと。
 * 設定はコンストラクタで受け取り、再読み込みの後に別のコンテナの設定と混ざらないようにする。
 *
 * @property config 登録したときのコンテナにある world tree 機能の設定
 */
class WorldTreeService(private val config: WorldTreeConfig) : MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()

    /**
     * 設定されている季節の名前 (コマンドの補完に使う)
     *
     * @return 季節の名前の一覧 (設定の順)
     */
    fun seasonNames(): List<String> = config.seasons.map { it.name }

    /**
     * 現在の月の季節にする (すでにその季節なら何もしない)
     *
     * @return 切り替えの結果
     */
    fun applyCurrentSeason(): WorldTreeApplyResult = apply(WorldTreeSeasonResolver.resolveNow(config), force = false)

    /**
     * 指定した季節にする (すでにその季節でも置き直す)
     *
     * @param name 季節の名前
     * @return 切り替えの結果
     */
    fun applySeason(name: String): WorldTreeApplyResult {
        val season = config.seasonByName(name) ?: return WorldTreeApplyResult.UnknownSeason(name)
        return apply(season, force = true)
    }

    /**
     * 世界樹の葉を季節のブロックへ置き換え、結果をログに残す
     *
     * @param season 目的の季節
     * @param force すでに目的の季節でも置き直すかどうか
     * @return 切り替えの結果
     */
    private fun apply(season: WorldTreeSeason, force: Boolean): WorldTreeApplyResult {
        check(plugin.server.isPrimaryThread) { "World tree must be updated on the main thread" }
        val result = applyCatching(season, force)
        log(result)
        return result
    }

    /**
     * 前提を確かめてから置き換え、例外を結果に変換する
     *
     * @param season 目的の季節
     * @param force すでに目的の季節でも置き直すかどうか
     * @return 切り替えの結果
     */
    @Suppress("TooGenericExceptionCaught")
    private fun applyCatching(season: WorldTreeSeason, force: Boolean): WorldTreeApplyResult {
        // WorldEdit の API クラスに触れる前に、Bukkit の API だけで存在を確認する
        if (!isWorldEditEnabled()) return WorldTreeApplyResult.WorldEditUnavailable
        val world = plugin.server.getWorld(config.world) ?: return WorldTreeApplyResult.WorldNotFound(config.world)
        return try {
            WorldEditWorldTreeEditor().apply(world, config, season, force)
        } catch (e: LinkageError) {
            // 互換性のない WorldEdit が導入されている場合のクラス解決の失敗
            plugin.logger.warning("World tree: WorldEdit integration failed (incompatible WorldEdit API?): $e")
            WorldTreeApplyResult.WorldEditUnavailable
        } catch (e: Exception) {
            // 書き込みの途中で失敗した可能性もあるため、スタックトレースごと残す
            plugin.logger.log(Level.WARNING, "World tree: failed to apply season '${season.name}'", e)
            WorldTreeApplyResult.Failed(e.message ?: e.toString())
        }
    }

    /**
     * 結果をサーバーのログに出す (失敗は警告として出し、原因を調べられるよう数え上げの結果も含める)
     *
     * @param result 切り替えの結果
     */
    private fun log(result: WorldTreeApplyResult) {
        val logger = plugin.logger
        when (result) {
            is WorldTreeApplyResult.Applied -> logger.info(
                "World tree: applied season '${result.season}' " +
                    "(reverted ${result.revertedBlocks} blocks, placed ${result.placedBlocks} blocks)",
            )
            is WorldTreeApplyResult.AlreadyApplied -> logger.info("World tree: season '${result.season}' is already applied")
            is WorldTreeApplyResult.CountMismatch -> logger.warning(
                "World tree: aborted switching to '${result.season}' without changes because the leaf count " +
                    "${result.decision.trackedCount} does not match expectedBlockCount ${result.decision.expectedCount}. " +
                    "Counts: ${result.decision.counts}",
            )
            is WorldTreeApplyResult.RevertCheckFailed -> logger.warning(
                "World tree: stopped at the intermediate blocks before applying '${result.season}' because the revert check failed. " +
                    "Remaining season blocks: ${result.check.remainingSeasonBlocks}, " +
                    "intermediate blocks: ${result.check.intermediateCount} (expected ${result.check.expectedCount})",
            )
            is WorldTreeApplyResult.InvalidBlocks -> logger.warning(
                "World tree: aborted without changes because some blocks in config.conf could not be resolved: ${result.errors}",
            )
            is WorldTreeApplyResult.UnknownSeason -> logger.warning("World tree: unknown season '${result.name}'")
            is WorldTreeApplyResult.WorldNotFound -> logger.warning("World tree: world '${result.world}' is not loaded")
            WorldTreeApplyResult.WorldEditUnavailable -> logger.warning("World tree: WorldEdit or FastAsyncWorldEdit is not enabled")
            // 例外の詳細は applyCatching でスタックトレースごと出力済み
            is WorldTreeApplyResult.Failed -> Unit
        }
    }

    /**
     * WorldEdit または FAWE が有効かどうかを判定する
     *
     * @return どちらかが有効なら true
     */
    private fun isWorldEditEnabled(): Boolean = WORLDEDIT_PLUGIN_NAMES.any { name ->
        plugin.server.pluginManager.getPlugin(name)?.isEnabled == true
    }

    companion object {
        /** WorldEdit の API を提供するプラグインの名前 (FAWE は FastAsyncWorldEdit という名前で動作する) */
        private val WORLDEDIT_PLUGIN_NAMES: List<String> = listOf("WorldEdit", "FastAsyncWorldEdit")
    }
}
