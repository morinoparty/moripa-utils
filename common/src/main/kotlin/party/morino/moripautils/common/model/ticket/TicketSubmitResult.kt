/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.ticket

import party.morino.moripautils.api.model.ticket.Ticket

/**
 * チケット送信の結果
 *
 * 入力の誤りは例外ではなく値として返し、UI 側で理由ごとのメッセージを出し分けられるようにする。
 */
sealed interface TicketSubmitResult {
    /**
     * 送信に成功した
     *
     * @property ticket 保存されたチケット
     */
    data class Success(
        val ticket: Ticket,
    ) : TicketSubmitResult

    /**
     * 設定に存在しないカテゴリーが指定された
     *
     * @property categoryId 指定されたカテゴリー id
     */
    data class UnknownCategory(
        val categoryId: String,
    ) : TicketSubmitResult

    /** 本文が空 (空白のみを含む) */
    data object BlankContent : TicketSubmitResult

    /**
     * 本文が長すぎる
     *
     * @property maxLength 許容される最大文字数
     */
    data class ContentTooLong(
        val maxLength: Int,
    ) : TicketSubmitResult
}
