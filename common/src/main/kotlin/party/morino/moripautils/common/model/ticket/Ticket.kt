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
 * プレイヤーから送信されたお問い合わせ (チケット)
 *
 * @property id データベースが採番する通し番号
 * @property serverId 送信元のサーバー (config.conf の server)
 * @property playerUuid 送信したプレイヤーの UUID
 * @property playerName 送信時点のプレイヤー名 (後から名前が変わっても送信時の表示を残す)
 * @property location 送信時にプレイヤーがいた場所
 * @property categoryId カテゴリーの id (config.conf の ticket.categories[].id)
 * @property content お問い合わせの本文
 * @property status 対応状況
 * @property createdAt 送信日時
 */
data class Ticket(
    val id: Long,
    val serverId: String,
    val playerUuid: UUID,
    val playerName: String,
    val location: TicketLocation,
    val categoryId: String,
    val content: String,
    val status: TicketStatus,
    val createdAt: Instant,
)
