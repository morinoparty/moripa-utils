/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.advancement.command

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.brigadier.suggestion.TooltipSuggestion
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.suggestion.Suggestion
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.advancement.AdvancementPermissions
import party.morino.moripautils.paper.advancement.AdvancementRestoreService
import party.morino.moripautils.paper.advancement.AdvancementSnapshotFormatter
import party.morino.moripautils.paper.advancement.SnapshotSelector
import party.morino.moripautils.paper.model.advancement.AdvancementRestoreResult
import party.morino.moripautils.paper.model.advancement.AdvancementSnapshotSummary
import java.util.logging.Level
import kotlin.coroutines.cancellation.CancellationException

/**
 * /mu advancement restore コマンド (HuskSync のスナップショットから進捗だけを復元する)
 *
 * <snapshot> には新しい順の番号 (最新が 1) かスナップショットの ID を指定する。
 * Tab 補完では番号を候補にし、tooltip に日時や進捗の数を表示する。
 * Brigadier は補完候補を文字列順に並べ直すが、整数の候補は数値順に並べるため、番号にすることで最新が先頭に来る。
 *
 * HuskSync は任意依存のため、コマンドは常に登録し、使えるかどうかは実行時に判定する。
 */
@Suppress("UnstableApiUsage")
class AdvancementCommand(
    private val service: AdvancementRestoreService = AdvancementRestoreService(),
    private val formatter: AdvancementSnapshotFormatter = AdvancementSnapshotFormatter(),
) {
    /**
     * スナップショットの進捗だけを、プレイヤーの現在のデータへ復元する
     *
     * @param source コマンドの実行元 (コンソールからも実行できる)
     * @param playerName 復元するプレイヤーの名前 (オフラインでもよい)
     * @param snapshot 新しい順の番号 (最新が 1) またはスナップショットの ID
     */
    @Command("mu advancement restore <player> <snapshot>")
    @Permission(AdvancementPermissions.RESTORE)
    @CommandDescription("HuskSync のスナップショットから進捗だけを復元します")
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun restore(
        source: CommandSourceStack,
        @Argument(value = "player", suggestions = PLAYER_SUGGESTIONS) playerName: String,
        @Argument(value = "snapshot", suggestions = SNAPSHOT_SUGGESTIONS) snapshot: String,
    ) {
        val sender = source.sender
        val plugin = MoripaUtilsKoinContext.getOrNull()?.getOrNull<MoripaUtils>()
        if (plugin == null) {
            sender.sendRichMessage("<red>MoripaUtils が有効になっていないため実行できません。")
            return
        }
        if (!service.isAvailable) {
            sender.sendRichMessage("<red>HuskSync が有効になっていないため実行できません。")
            return
        }
        val selector = SnapshotSelector.parse(snapshot)
        if (selector == null) {
            sender.sendRichMessage("<red>スナップショットは 1 以上の番号 (最新が 1) か、スナップショットの ID で指定してください。")
            return
        }

        try {
            val listing = service.listingForExecution(sender.name, playerName, selector)
            if (listing == null) {
                sender.sendRichMessage("<red><player> のデータは HuskSync に記録されていません。", player(playerName))
                return
            }
            val target = selector.select(listing.snapshots)
            if (target == null) {
                sender.sendRichMessage(
                    "<red><player> のスナップショット <snapshot> が見つかりません。",
                    player(listing.playerName),
                    Placeholder.unparsed("snapshot", snapshot),
                )
                return
            }
            sender.sendRichMessage(
                "<gray><player> の進捗を #<index> (<description>) から復元しています...",
                player(listing.playerName),
                Placeholder.unparsed("index", target.index.toString()),
                Placeholder.unparsed("description", formatter.describe(target)),
            )
            val result = service.restore(listing, target.id, plugin.minecraftDispatcher)
            showResult(sender, listing.playerName, target, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            // HuskSync の API が想定と異なる版の場合
            plugin.logger.log(Level.WARNING, "Advancement restore: HuskSync integration failed", e)
            sender.sendRichMessage("<red>HuskSync との連携に失敗しました。詳細はサーバーのログを確認してください。")
        } catch (e: Exception) {
            // スナップショットの展開の失敗 (別の Minecraft の版で保存されたデータなど) は HuskSync の Future から例外として届く
            plugin.logger.log(Level.WARNING, "Advancement restore: failed to restore advancements of $playerName", e)
            sender.sendRichMessage(
                "<red>進捗の復元に失敗しました: <error>",
                Placeholder.unparsed("error", e.message ?: e.toString()),
            )
        }
    }

    /**
     * プレイヤー名の Tab 補完候補を返す (オンラインのプレイヤー)
     *
     * @param context コマンドの実行コンテキスト
     * @param input 入力途中の文字列 (絞り込みは Cloud が行うため使わない)
     * @return オンラインのプレイヤー名
     */
    @Suggestions(PLAYER_SUGGESTIONS)
    @Suppress("UnusedParameter")
    fun suggestPlayers(context: CommandContext<CommandSourceStack>, input: String): List<String> =
        context.sender().sender.server.onlinePlayers.map { it.name }

    /**
     * 指定したプレイヤーのスナップショットの Tab 補完候補を返す
     *
     * 候補は新しい順の番号 (最新が 1) で、tooltip に日時、進捗の数、保存された理由を表示する。
     *
     * @param context コマンドの実行コンテキスト (入力済みの <player> を使う)
     * @param input 入力途中の文字列 (絞り込みは Cloud が行うため使わない)
     * @return スナップショットの候補 (取得できない場合は空)
     */
    @Suggestions(SNAPSHOT_SUGGESTIONS)
    @Suppress("UnusedParameter", "TooGenericExceptionCaught")
    suspend fun suggestSnapshots(context: CommandContext<CommandSourceStack>, input: String): List<Suggestion> {
        val playerName = context.getOrDefault<String>("player", null) ?: return emptyList()
        if (!service.isAvailable) {
            return emptyList()
        }
        val listing = try {
            service.listingForSuggestions(context.sender().sender.name, playerName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 補完の失敗はコマンドの実行時に改めて伝えるため、ここでは候補を出さないだけにする
            MoripaUtilsKoinContext.getOrNull()?.getOrNull<MoripaUtils>()?.logger
                ?.fine("Advancement restore: failed to list snapshots of $playerName: $e")
            null
        } catch (e: LinkageError) {
            MoripaUtilsKoinContext.getOrNull()?.getOrNull<MoripaUtils>()?.logger
                ?.fine("Advancement restore: HuskSync integration failed: $e")
            null
        } ?: return emptyList()
        return listing.snapshots.map { summary ->
            TooltipSuggestion.suggestion(
                summary.index.toString(),
                MessageComponentSerializer.message().serialize(Component.text(formatter.describe(summary))),
            )
        }
    }

    /**
     * 復元の結果を実行者に表示する
     *
     * @param sender 結果を表示する相手
     * @param playerName プレイヤー名
     * @param target 進捗を取り出したスナップショット
     * @param result 復元の結果
     */
    private fun showResult(
        sender: CommandSender,
        playerName: String,
        target: AdvancementSnapshotSummary,
        result: AdvancementRestoreResult,
    ) {
        when (result) {
            // UUID には MiniMessage のタグとして解釈される文字が含まれないため、そのまま埋め込む
            is AdvancementRestoreResult.Restored -> sender.sendRichMessage(
                "<green><player> の進捗を <timestamp> のスナップショットから復元しました。(進捗 <count>)<newline>" +
                    "<gray>復元前のデータは固定したスナップショット " +
                    "<click:copy_to_clipboard:'${result.backupId}'><hover:show_text:'クリックで ID をコピー'>" +
                    "<yellow>${result.backupId.toString().take(AdvancementSnapshotFormatter.SHORT_ID_LENGTH)}</yellow>" +
                    "</hover></click> に保存しました。/userdata restore で戻せます。",
                player(playerName),
                Placeholder.unparsed("timestamp", formatter.timestamp(target)),
                Placeholder.unparsed("count", result.advancementCount.toString()),
            )
            AdvancementRestoreResult.PlayerNotFound ->
                sender.sendRichMessage("<red><player> のデータは HuskSync に記録されていません。", player(playerName))
            AdvancementRestoreResult.SnapshotNotFound ->
                sender.sendRichMessage("<red>スナップショットが見つかりません。削除された可能性があります。")
            AdvancementRestoreResult.NoAdvancementData ->
                sender.sendRichMessage("<red>このスナップショットには進捗のデータが含まれていません。")
            AdvancementRestoreResult.NoCurrentData ->
                sender.sendRichMessage("<red><player> の現在のデータを取得できませんでした。", player(playerName))
            AdvancementRestoreResult.BackupFailed ->
                sender.sendRichMessage("<red>復元前のデータを保存できなかったため、復元を中止しました。")
            is AdvancementRestoreResult.RestoreFailed -> sender.sendRichMessage(
                "<red>進捗を差し替えたデータの保存を確認できませんでした (時間切れ、または DataSaveEvent のキャンセル)。<newline>" +
                    "<gray>復元前のデータを保存したスナップショット: <yellow>${result.backupId}</yellow>",
            )
        }
    }

    /**
     * プレイヤー名のプレースホルダー (名前に MiniMessage のタグとして解釈される文字が含まれうるため unparsed で埋め込む)
     *
     * @param name プレイヤー名
     * @return プレースホルダー
     */
    private fun player(name: String) = Placeholder.unparsed("player", name)

    companion object {
        /** プレイヤー名の補完候補の名前 */
        private const val PLAYER_SUGGESTIONS = "advancement-restore-players"

        /** スナップショットの補完候補の名前 */
        private const val SNAPSHOT_SUGGESTIONS = "advancement-restore-snapshots"
    }
}
