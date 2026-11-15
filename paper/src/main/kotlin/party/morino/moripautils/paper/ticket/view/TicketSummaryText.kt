/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket.view

import party.morino.moripautils.api.model.ticket.Ticket
import party.morino.moripautils.common.model.config.TicketConfig
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * チケットの概要を 1 行の文字列にする (/ticket list の一覧と、チケット id の Tab 補完の tooltip で使う)
 *
 * 外部状態に依存しない純粋関数だけを持つ。
 */
object TicketSummaryText {
    /** 本文のプレビューの最大文字数 */
    const val PREVIEW_LENGTH: Int = 30

    /** 改行を含む連続した空白 */
    private val WHITESPACE: Regex = Regex("\\s+")

    /** 日時の表示形式 */
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    /**
     * カテゴリーの表示名を返す
     *
     * @param config チケットの設定
     * @param ticket 対象のチケット
     * @return カテゴリーの表示名 (設定からカテゴリーが削除されている場合は id)
     */
    fun categoryName(config: TicketConfig, ticket: Ticket): String =
        config.categories.firstOrNull { it.id == ticket.categoryId }?.name ?: ticket.categoryId

    /**
     * 作成日時を表示用にする
     *
     * @param ticket 対象のチケット
     * @param zone 表示に使うタイムゾーン
     * @return yyyy/MM/dd HH:mm 形式の日時
     */
    fun date(ticket: Ticket, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_FORMATTER.format(ticket.createdAt.atZone(zone))

    /**
     * 本文の先頭を 1 行に収まるようにする
     *
     * 改行や連続する空白を 1 つの空白につぶし、[PREVIEW_LENGTH] 文字を超える部分は「…」で省略する。
     *
     * @param content 本文
     * @return 本文のプレビュー
     */
    fun preview(content: String): String {
        val flattened = content.trim().replace(WHITESPACE, " ")
        return if (flattened.length > PREVIEW_LENGTH) flattened.take(PREVIEW_LENGTH) + "…" else flattened
    }

    /**
     * チケット id の Tab 補完の tooltip に表示する説明にする
     *
     * @param ticket 対象のチケット
     * @param categoryName カテゴリーの表示名
     * @param zone 表示に使うタイムゾーン
     * @return 対応状況、カテゴリー、作成日時、送信者、本文のプレビューを並べた文字列
     */
    fun tooltip(ticket: Ticket, categoryName: String, zone: ZoneId = ZoneId.systemDefault()): String =
        "[${TicketStatusLabel.of(ticket)}] $categoryName ${date(ticket, zone)} ${ticket.playerName}: ${preview(ticket.content)}"
}
