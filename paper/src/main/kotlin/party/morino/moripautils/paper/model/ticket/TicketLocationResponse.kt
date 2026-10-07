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
import party.morino.moripautils.common.model.ticket.TicketLocation

/**
 * MineAuth の HTTP API で返す、チケット送信時の場所
 *
 * @property world ワールド名
 * @property x ブロックの X 座標
 * @property y ブロックの Y 座標
 * @property z ブロックの Z 座標
 */
@Serializable
data class TicketLocationResponse(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    companion object {
        /**
         * ドメインモデルからレスポンスを作る
         *
         * @param location 変換する場所
         * @return レスポンス
         */
        fun from(location: TicketLocation): TicketLocationResponse = TicketLocationResponse(
            world = location.world,
            x = location.x,
            y = location.y,
            z = location.z,
        )
    }
}
