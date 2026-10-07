/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.model.ticket

import kotlinx.serialization.Serializable
import party.morino.moripautils.common.model.ticket.TicketComment

/**
 * MineAuth の HTTP API で返すコメント 1 件分のレスポンス
 *
 * @property id コメントの id
 * @property ticketId コメント先のチケットの id
 * @property authorUuid 書き込んだプレイヤーの UUID (サービストークンからの書き込みでは null)
 * @property authorName 書き込んだ時点の名前
 * @property authorType 書き込んだ人の立場 (PLAYER / STAFF)
 * @property content 本文
 * @property createdAt 書き込み日時 (ISO-8601 形式の UTC)
 */
@Serializable
data class TicketCommentResponse(
    val id: Long,
    val ticketId: Long,
    val authorUuid: String?,
    val authorName: String,
    val authorType: String,
    val content: String,
    val createdAt: String,
) {
    companion object {
        /**
         * ドメインモデルからレスポンスを作る
         *
         * @param comment 変換するコメント
         * @return レスポンス
         */
        fun from(comment: TicketComment): TicketCommentResponse = TicketCommentResponse(
            id = comment.id,
            ticketId = comment.ticketId,
            authorUuid = comment.authorUuid?.toString(),
            authorName = comment.authorName,
            authorType = comment.authorType.name,
            content = comment.content,
            createdAt = comment.createdAt.toString(),
        )
    }
}
