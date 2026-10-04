/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.ticket

import java.time.Instant
import java.util.UUID

/**
 * チケットに書き込まれたコメント
 *
 * @property id データベースが採番する通し番号 (全チケットで共通)
 * @property ticketId コメント先のチケットの id
 * @property authorUuid 書き込んだプレイヤーの UUID (サービストークンからの書き込みでは null)
 * @property authorName 書き込んだ時点の名前 (後から名前が変わっても書き込み時の表示を残す)
 * @property authorType 書き込んだ人の立場
 * @property content コメントの本文
 * @property createdAt 書き込み日時
 */
data class TicketComment(
    val id: Long,
    val ticketId: Long,
    val authorUuid: UUID?,
    val authorName: String,
    val authorType: TicketCommentAuthorType,
    val content: String,
    val createdAt: Instant,
)
