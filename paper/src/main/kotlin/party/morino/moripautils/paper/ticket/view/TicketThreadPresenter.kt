/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket.view

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.TicketConfig
import party.morino.moripautils.api.model.ticket.Ticket
import party.morino.moripautils.api.model.ticket.TicketComment
import party.morino.moripautils.api.model.ticket.TicketCommentAuthorType
import party.morino.moripautils.api.model.ticket.TicketStatus
import party.morino.moripautils.paper.ticket.TicketPermissions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * チケットとコメントのスレッドをチャットに表示する (/ticket view)
 *
 * 本文やプレイヤー名に MiniMessage のタグが含まれていても解釈させないよう、すべて unparsed で埋め込む。
 */
class TicketThreadPresenter : MoripaUtilsKoinComponent {
    private val config: TicketConfig by inject()

    /**
     * スレッドを表示する
     *
     * @param sender 表示先 (プレイヤーまたはコンソール)
     * @param ticket 表示するチケット
     * @param comments 表示するコメント (古い順)
     * @param hasOlder 表示しきれなかった古いコメントがあるかどうか
     */
    fun present(sender: CommandSender, ticket: Ticket, comments: List<TicketComment>, hasOlder: Boolean) {
        // 設定からカテゴリーが削除されている場合は id をそのまま表示する
        val categoryName = config.categories.firstOrNull { it.id == ticket.categoryId }?.name ?: ticket.categoryId
        sender.sendRichMessage(
            "<gold>===== お問い合わせ #<id> =====",
            Placeholder.unparsed("id", ticket.id.toString()),
        )
        sender.sendRichMessage(
            "<gray><category> / <status> / <player> / <date>",
            Placeholder.unparsed("category", categoryName),
            Placeholder.unparsed("status", TicketStatusLabel.of(ticket)),
            Placeholder.unparsed("player", ticket.playerName),
            Placeholder.unparsed("date", format(ticket.createdAt)),
        )
        sender.sendRichMessage("<white><content>", Placeholder.unparsed("content", ticket.content))

        if (comments.isEmpty()) {
            sender.sendRichMessage("<gray>コメントはまだありません。")
        } else {
            sender.sendRichMessage("<gold>----- コメント -----")
            // チャットに収まらないほど古いコメントは Web (MineAuth) で確認してもらう
            if (hasOlder) {
                sender.sendRichMessage("<gray>(これより前のコメントは省略しています)")
            }
            comments.forEach { presentComment(sender, it) }
        }
        // 返信や対応をしやすいよう、操作できるコマンドのボタンを添える (id は数値なのでそのまま埋め込める)
        // コンソールはボタンを押せず、コメントやテレポートも行えないため、プレイヤーにだけ表示する
        if (sender is Player) {
            sender.sendRichMessage(actionButtons(sender, ticket).joinToString(" "))
        }
    }

    /**
     * チケットに対して実行できる操作のボタンを組み立てる
     *
     * @param player 表示先のプレイヤー
     * @param ticket 表示するチケット
     * @return MiniMessage 形式のボタンの一覧
     */
    private fun actionButtons(player: Player, ticket: Ticket): List<String> = buildList {
        val isStaff = player.hasPermission(TicketPermissions.STAFF)
        add("<click:run_command:'/ticket comment ${ticket.id}'><aqua>[コメントする]</aqua></click>")
        when (ticket.status) {
            // クローズは本人も行えるが、理由を選べるよう入力欄に途中まで入れるだけにする
            TicketStatus.OPEN ->
                add("<click:suggest_command:'/ticket close ${ticket.id} --reason-type '><red>[クローズ]</red></click>")
            // 再オープンは運営だけが行える
            TicketStatus.CLOSED -> if (isStaff) {
                add("<click:run_command:'/ticket reopen ${ticket.id}'><gold>[再オープン]</gold></click>")
            }
        }
        if (isStaff) {
            add("<click:run_command:'/ticket teleport ${ticket.id}'><green>[テレポート]</green></click>")
        }
    }

    /**
     * コメントを 1 件表示する
     *
     * @param sender 表示先 (プレイヤーまたはコンソール)
     * @param comment 表示するコメント
     */
    private fun presentComment(sender: CommandSender, comment: TicketComment) {
        // 運営の書き込みは見分けやすいよう印と色を付ける
        val header = when (comment.authorType) {
            TicketCommentAuthorType.STAFF -> "<green>[運営] <author></green> <gray><date>"
            TicketCommentAuthorType.PLAYER -> "<yellow><author></yellow> <gray><date>"
        }
        sender.sendRichMessage(
            header,
            Placeholder.unparsed("author", comment.authorName),
            Placeholder.unparsed("date", format(comment.createdAt)),
        )
        sender.sendRichMessage("<white><content>", Placeholder.unparsed("content", comment.content))
    }

    /**
     * 日時をサーバーのタイムゾーンで表示用の文字列にする
     *
     * @param instant 日時
     * @return yyyy/MM/dd HH:mm 形式の文字列
     */
    private fun format(instant: Instant): String = DATE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))

    companion object {
        /** 表示する最新のコメントの件数 (チャット欄が流れすぎないようにする) */
        const val MAX_COMMENTS: Int = 10

        /** 日時の表示形式 */
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    }
}
