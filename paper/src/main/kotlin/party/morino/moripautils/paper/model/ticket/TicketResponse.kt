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
import party.morino.moripautils.common.model.ticket.Ticket

/**
 * MineAuth の HTTP API で返すチケット 1 件分のレスポンス
 *
 * @property id チケットの id
 * @property server 送信元のサーバー
 * @property playerUuid 送信者の UUID
 * @property playerName 送信時点のプレイヤー名
 * @property location 送信時にプレイヤーがいた場所
 * @property categoryId カテゴリーの id
 * @property categoryName カテゴリーの表示名 (設定からカテゴリーが削除されている場合は null)
 * @property content 本文
 * @property status 対応状況 (OPEN / CLOSED)
 * @property closeReason クローズした理由 (done / not-planned / duplicate、オープン中は null)
 * @property createdAt 送信日時 (ISO-8601 形式の UTC)
 */
@Serializable
data class TicketResponse(
    val id: Long,
    val server: String,
    val playerUuid: String,
    val playerName: String,
    val location: TicketLocationResponse,
    val categoryId: String,
    val categoryName: String?,
    val content: String,
    val status: String,
    val createdAt: String,
    val closeReason: String?,
) {
    companion object {
        /**
         * ドメインモデルからレスポンスを作る
         *
         * @param ticket 変換するチケット
         * @param categoryName カテゴリーの表示名 (見つからなければ null)
         * @return レスポンス
         */
        fun from(ticket: Ticket, categoryName: String?): TicketResponse = TicketResponse(
            id = ticket.id,
            server = ticket.serverId,
            playerUuid = ticket.playerUuid.toString(),
            playerName = ticket.playerName,
            location = TicketLocationResponse.from(ticket.location),
            categoryId = ticket.categoryId,
            categoryName = categoryName,
            content = ticket.content,
            status = ticket.status.name,
            createdAt = ticket.createdAt.toString(),
            closeReason = ticket.closeReason?.id,
        )
    }
}
