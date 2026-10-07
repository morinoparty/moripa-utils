/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.ticket.view

import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketCloseReason
import party.morino.moripautils.common.model.ticket.TicketStatus

/**
 * チケットの対応状況をプレイヤー向けの表示名に変換する
 *
 * 外部状態に依存しない純粋関数だけを持つ。
 */
object TicketStatusLabel {
    /**
     * チケットの対応状況の表示名を返す
     *
     * @param ticket 対象のチケット
     * @return 表示名 (例: 「オープン」「クローズ (対応完了)」)
     */
    fun of(ticket: Ticket): String = when (ticket.status) {
        TicketStatus.OPEN -> "オープン"
        TicketStatus.CLOSED -> "クローズ (${of(ticket.closeReason ?: TicketCloseReason.DEFAULT)})"
    }

    /**
     * クローズした理由の表示名を返す
     *
     * @param reason クローズした理由
     * @return 表示名
     */
    fun of(reason: TicketCloseReason): String = when (reason) {
        TicketCloseReason.DONE -> "対応完了"
        TicketCloseReason.NOT_PLANNED -> "対応しない"
        TicketCloseReason.DUPLICATE -> "重複"
    }
}
