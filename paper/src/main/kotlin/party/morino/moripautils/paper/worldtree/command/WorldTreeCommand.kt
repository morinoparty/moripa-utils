/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.worldtree.command

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.model.worldtree.WorldTreeApplyResult
import party.morino.moripautils.paper.worldtree.WorldTreePermissions
import party.morino.moripautils.paper.worldtree.WorldTreeService

/**
 * /mu worldtree コマンド (世界樹の季節をその場で切り替える)
 *
 * 切り替えはその場限りで、次回のサーバーの起動時には現在の月の季節に戻る。
 * ブートストラップ段階で登録するため、生成時点では Koin コンテナがまだ存在しない。
 * また再読み込みのたびにコンテナが作り直されるため、サービスはコマンドの実行時にコンテナから取り出す。
 */
@Suppress("UnstableApiUsage")
class WorldTreeCommand {
    /**
     * 指定した季節にする (すでにその季節でも置き直す)
     *
     * @param source コマンドの実行元 (コンソールからも実行できる)
     * @param season 季節の名前
     */
    @Command("mu worldtree set <season>")
    @Permission(WorldTreePermissions.MANAGE)
    @CommandDescription("世界樹を指定した季節にします")
    suspend fun set(
        source: CommandSourceStack,
        @Argument(value = "season", suggestions = SEASON_SUGGESTIONS) season: String,
    ) {
        run(source.sender) { service -> service.applySeason(season) }
    }

    /**
     * 現在の月の季節にする (すでにその季節なら何もしない)
     *
     * @param source コマンドの実行元 (コンソールからも実行できる)
     */
    @Command("mu worldtree auto")
    @Permission(WorldTreePermissions.MANAGE)
    @CommandDescription("世界樹を現在の月の季節にします")
    suspend fun auto(source: CommandSourceStack) {
        run(source.sender) { service -> service.applyCurrentSeason() }
    }

    /**
     * 季節の名前の Tab 補完候補を返す
     *
     * @param context コマンドの実行コンテキスト
     * @param input 入力途中の文字列 (絞り込みは Cloud が行うため使わない)
     * @return 補完候補の季節の名前 (機能が無効な場合は空)
     */
    @Suggestions(SEASON_SUGGESTIONS)
    @Suppress("UnusedParameter")
    fun suggestSeasons(context: CommandContext<CommandSourceStack>, input: String): List<String> =
        MoripaUtilsKoinContext.getOrNull()?.getOrNull<WorldTreeService>()?.seasonNames().orEmpty()

    /**
     * 機能が使えることを確かめてから、メインスレッドで切り替えて結果を表示する
     *
     * @param sender 結果を表示する相手
     * @param action サービスを使って切り替える処理
     */
    private suspend fun run(sender: CommandSender, action: (WorldTreeService) -> WorldTreeApplyResult) {
        // プラグインの有効化に失敗した場合はコンテナが存在しない
        val plugin = MoripaUtilsKoinContext.getOrNull()?.getOrNull<MoripaUtils>()
        if (plugin == null) {
            sender.sendRichMessage("<red>MoripaUtils が有効になっていないため実行できません。")
            return
        }

        // 範囲が広く置き換えに時間がかかるため、先に受け付けたことを伝える
        sender.sendRichMessage("<gray>世界樹のブロックを置き換えています...")
        // ブロックの書き換えはメインスレッドで行う必要がある (Cloud は非同期のコーディネーターで実行する)
        // /mu reload もメインスレッドで動くため、サービスはメインスレッドに移ってから取り出し、実行時点のコンテナと設定を使う
        val result = withContext(plugin.minecraftDispatcher) {
            // 機能が無効な場合はサービスが Koin に登録されていない
            MoripaUtilsKoinContext.getOrNull()?.getOrNull<WorldTreeService>()?.let(action)
        }
        if (result == null) {
            sender.sendRichMessage("<red>world tree 機能が無効です。config.conf の worldTree.enabled を確認してください。")
            return
        }
        showResult(sender, result)
    }

    /**
     * 結果を実行者に表示する
     *
     * 季節の名前やエラーには MiniMessage のタグとして解釈される文字が含まれうるため、unparsed で埋め込む。
     *
     * @param sender 結果を表示する相手
     * @param result 切り替えの結果
     */
    private fun showResult(sender: CommandSender, result: WorldTreeApplyResult) {
        when (result) {
            is WorldTreeApplyResult.Applied -> sender.sendRichMessage(
                "<green>世界樹を <season> にしました。(<count> ブロック)",
                season(result.season),
                Placeholder.unparsed("count", result.placedBlocks.toString()),
            )
            is WorldTreeApplyResult.AlreadyApplied ->
                sender.sendRichMessage("<yellow>世界樹はすでに <season> です。", season(result.season))
            is WorldTreeApplyResult.CountMismatch -> sender.sendRichMessage(
                "<red>葉の数 (<actual>) が設定 (<expected>) と一致しないため、何も変更していません。詳細はサーバーのログを確認してください。",
                Placeholder.unparsed("actual", result.decision.trackedCount.toString()),
                Placeholder.unparsed("expected", result.decision.expectedCount.toString()),
            )
            is WorldTreeApplyResult.RevertCheckFailed -> sender.sendRichMessage(
                "<red>中間ブロックへ戻した後の確認に失敗したため、中間ブロックのまま止めました。詳細はサーバーのログを確認してください。",
            )
            is WorldTreeApplyResult.InvalidBlocks -> sender.sendRichMessage(
                "<red>config.conf のブロックを解釈できないため、何も変更していません: <errors>",
                Placeholder.unparsed("errors", result.errors.joinToString(", ")),
            )
            is WorldTreeApplyResult.UnknownSeason -> sender.sendRichMessage(
                "<red>季節 <season> は設定されていません。",
                season(result.name),
            )
            is WorldTreeApplyResult.WorldNotFound -> sender.sendRichMessage(
                "<red>ワールド <world> が読み込まれていません。",
                Placeholder.unparsed("world", result.world),
            )
            WorldTreeApplyResult.WorldEditUnavailable ->
                sender.sendRichMessage("<red>WorldEdit (または FAWE) が利用できないため実行できません。")
            is WorldTreeApplyResult.Failed ->
                sender.sendRichMessage("<red>置き換えに失敗しました。詳細はサーバーのログを確認してください。")
        }
    }

    /**
     * 季節の名前を埋め込むプレースホルダーを作る
     *
     * @param name 季節の名前
     * @return season タグのプレースホルダー
     */
    private fun season(name: String): TagResolver = Placeholder.unparsed("season", name)

    companion object {
        /** 季節の名前の補完候補の名前 */
        const val SEASON_SUGGESTIONS: String = "worldtree-seasons"
    }
}
