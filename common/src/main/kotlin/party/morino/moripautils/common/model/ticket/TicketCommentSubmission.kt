/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.ticket

import java.util.UUID

/**
 * 新しく保存するコメントの内容 (id / 書き込み日時はリポジトリ側で決める)
 *
 * @property ticketId 存在を確認済みのチケットの id
 * @property authorUuid 書き込んだプレイヤーの UUID (サービストークンからの書き込みでは null)
 * @property authorName 書き込んだ時点の名前
 * @property authorType 判定済みの書き込んだ人の立場
 * @property content 検証済みの本文 (前後の空白は取り除いてある)
 */
data class TicketCommentSubmission(
    val ticketId: Long,
    val authorUuid: UUID?,
    val authorName: String,
    val authorType: TicketCommentAuthorType,
    val content: String,
)
