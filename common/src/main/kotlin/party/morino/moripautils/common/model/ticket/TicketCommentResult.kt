/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.ticket

/**
 * コメント書き込みの結果
 *
 * 入力の誤りは例外ではなく値として返し、UI 側で理由ごとのメッセージを出し分けられるようにする。
 */
sealed interface TicketCommentResult {
    /**
     * 書き込みに成功した
     *
     * @property ticket コメント先のチケット
     * @property comment 保存されたコメント
     */
    data class Success(
        val ticket: Ticket,
        val comment: TicketComment,
    ) : TicketCommentResult

    /** チケットが存在しない、または操作する権限がない (どちらかを区別させないため同じ結果にする) */
    data object TicketNotFound : TicketCommentResult

    /** 本文が空 (空白のみを含む) */
    data object BlankContent : TicketCommentResult

    /**
     * 本文が長すぎる
     *
     * @property maxLength 許容される最大文字数
     */
    data class ContentTooLong(
        val maxLength: Int,
    ) : TicketCommentResult
}
