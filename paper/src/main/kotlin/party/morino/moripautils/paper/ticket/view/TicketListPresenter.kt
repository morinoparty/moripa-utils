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
import party.morino.moripautils.common.model.ticket.TicketListFilter
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 閲覧できるチケットの一覧をチャットに表示する (/ticket list)
 *
 * 本文やカテゴリー名に MiniMessage のタグが含まれていても解釈させないよう、すべて unparsed で埋め込む。
 */
class TicketListPresenter : MoripaUtilsKoinComponent {
    private val config: TicketConfig by inject()

    /**
     * 一覧の 1 ページ分を表示する
     *
     * @param player 表示先のプレイヤー
     * @param tickets 表示するチケット (新しい順、最大 [PAGE_SIZE] 件)
     * @param page 表示しているページ番号 (1 始まり)
     * @param hasNext 次のページがあるかどうか
     * @param filter 一覧の絞り込み条件 (ページ移動のボタンにも引き継ぐ)
     */
    fun present(player: Player, tickets: List<Ticket>, page: Int, hasNext: Boolean, filter: TicketListFilter) {
        if (tickets.isEmpty()) {
            // 1 ページ目が空ならチケット自体が無く、それ以降なら範囲外のページを指定している
            val message = if (page == 1) "<gray>お問い合わせはまだありません。" else "<gray>このページにはお問い合わせがありません。"
            player.sendRichMessage(message)
            return
        }
        player.sendRichMessage(
            "<gold>===== お問い合わせ一覧 (<page> ページ目) =====",
            Placeholder.unparsed("page", page.toString()),
        )
        tickets.forEach { presentTicket(player, it) }
        presentPagination(player, page, hasNext, filter)
    }

    /**
     * チケットを 1 行で表示する (クリックすると /ticket view で詳細を開く)
     *
     * @param player 表示先のプレイヤー
     * @param ticket 表示するチケット
     */
    private fun presentTicket(player: Player, ticket: Ticket) {
        // 設定からカテゴリーが削除されている場合は id をそのまま表示する
        val categoryName = config.categories.firstOrNull { it.id == ticket.categoryId }?.name ?: ticket.categoryId
        // 1 行に収まるよう、改行や連続する空白をつぶして先頭だけを表示する
        val flattened = ticket.content.replace(WHITESPACE, " ")
        val preview = if (flattened.length > PREVIEW_LENGTH) flattened.take(PREVIEW_LENGTH) + "…" else flattened
        // id は数値なのでクリックイベントにそのまま埋め込める
        player.sendRichMessage(
            "<click:run_command:'/ticket view ${ticket.id}'><hover:show_text:'<gray>クリックで詳細を表示'>" +
                "<aqua>#<id></aqua> <gray>[<status>] <category> <date></gray> <white><preview></white></hover></click>",
            Placeholder.unparsed("id", ticket.id.toString()),
            Placeholder.unparsed("status", TicketStatusLabel.of(ticket)),
            Placeholder.unparsed("category", categoryName),
            Placeholder.unparsed("date", DATE_FORMATTER.format(ticket.createdAt.atZone(ZoneId.systemDefault()))),
            Placeholder.unparsed("preview", preview),
        )
    }

    /**
     * 前後のページへ移動するボタンを表示する
     *
     * @param player 表示先のプレイヤー
     * @param page 表示しているページ番号 (1 始まり)
     * @param hasNext 次のページがあるかどうか
     * @param filter 一覧の絞り込み条件 (ボタンのコマンドに付け直す)
     */
    private fun presentPagination(player: Player, page: Int, hasNext: Boolean, filter: TicketListFilter) {
        // 1 ページで収まる場合はボタンを出さない
        if (page <= 1 && !hasNext) {
            return
        }
        val flags = filterFlags(filter)
        val buttons = buildList {
            if (page > 1) {
                add("<click:run_command:'/ticket list ${page - 1}$flags'><aqua>[前のページ]</aqua></click>")
            }
            if (hasNext) {
                add("<click:run_command:'/ticket list ${page + 1}$flags'><aqua>[次のページ]</aqua></click>")
            }
        }
        player.sendRichMessage(buttons.joinToString(" "))
    }

    /**
     * 絞り込み条件をコマンドのフラグ文字列に戻す
     *
     * プレイヤー名は英数字とアンダースコアだけを想定し、クリックイベントの文字列を壊す文字を含む場合は引き継がない。
     *
     * @param filter 一覧の絞り込み条件
     * @return 先頭に空白を付けたフラグ文字列 (条件が無ければ空文字)
     */
    private fun filterFlags(filter: TicketListFilter): String = buildString {
        filter.status?.let { append(" --status ").append(it.name.lowercase()) }
        filter.playerName?.takeIf { SAFE_PLAYER_NAME.matches(it) }?.let { append(" --player ").append(it) }
    }

    companion object {
        /** 1 ページに表示する件数 (チャット欄が流れすぎないようにする) */
        const val PAGE_SIZE: Int = 10

        /** 本文のプレビューの最大文字数 */
        private const val PREVIEW_LENGTH: Int = 30

        /** 改行を含む連続した空白 */
        private val WHITESPACE: Regex = Regex("\\s+")

        /** クリックイベントに埋め込んでも安全なプレイヤー名 (Bedrock 連携の接頭辞 . も許可する) */
        private val SAFE_PLAYER_NAME: Regex = Regex("^[A-Za-z0-9_.]+$")

        /** 日時の表示形式 */
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    }
}
