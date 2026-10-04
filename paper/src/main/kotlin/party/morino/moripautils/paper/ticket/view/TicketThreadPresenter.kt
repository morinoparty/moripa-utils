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
import org.bukkit.entity.Player
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.TicketConfig
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketCommentAuthorType
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
     * @param player 表示先のプレイヤー
     * @param ticket 表示するチケット
     * @param comments 表示するコメント (古い順)
     * @param hasOlder 表示しきれなかった古いコメントがあるかどうか
     */
    fun present(player: Player, ticket: Ticket, comments: List<TicketComment>, hasOlder: Boolean) {
        // 設定からカテゴリーが削除されている場合は id をそのまま表示する
        val categoryName = config.categories.firstOrNull { it.id == ticket.categoryId }?.name ?: ticket.categoryId
        player.sendRichMessage(
            "<gold>===== お問い合わせ #<id> =====",
            Placeholder.unparsed("id", ticket.id.toString()),
        )
        player.sendRichMessage(
            "<gray><category> / <status> / <player> / <date>",
            Placeholder.unparsed("category", categoryName),
            Placeholder.unparsed("status", ticket.status.name),
            Placeholder.unparsed("player", ticket.playerName),
            Placeholder.unparsed("date", format(ticket.createdAt)),
        )
        player.sendRichMessage("<white><content>", Placeholder.unparsed("content", ticket.content))

        if (comments.isEmpty()) {
            player.sendRichMessage("<gray>コメントはまだありません。")
        } else {
            player.sendRichMessage("<gold>----- コメント -----")
            // チャットに収まらないほど古いコメントは Web (MineAuth) で確認してもらう
            if (hasOlder) {
                player.sendRichMessage("<gray>(これより前のコメントは省略しています)")
            }
            comments.forEach { presentComment(player, it) }
        }
        // 返信しやすいよう、コメント用の Dialog を開くボタンを添える (id は数値なのでそのまま埋め込める)
        player.sendRichMessage(
            "<click:run_command:'/ticket comment ${ticket.id}'><aqua>[コメントする]</aqua></click>",
        )
    }

    /**
     * コメントを 1 件表示する
     *
     * @param player 表示先のプレイヤー
     * @param comment 表示するコメント
     */
    private fun presentComment(player: Player, comment: TicketComment) {
        // 運営の書き込みは見分けやすいよう印と色を付ける
        val header = when (comment.authorType) {
            TicketCommentAuthorType.STAFF -> "<green>[運営] <author></green> <gray><date>"
            TicketCommentAuthorType.PLAYER -> "<yellow><author></yellow> <gray><date>"
        }
        player.sendRichMessage(
            header,
            Placeholder.unparsed("author", comment.authorName),
            Placeholder.unparsed("date", format(comment.createdAt)),
        )
        player.sendRichMessage("<white><content>", Placeholder.unparsed("content", comment.content))
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
