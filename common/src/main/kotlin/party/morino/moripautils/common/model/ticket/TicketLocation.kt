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
 * チケットを送信したときにプレイヤーがいた場所
 *
 * 運営が現地を確認できるよう、ブロック単位の座標で保存する。
 *
 * @property world ワールド名 (例: world, world_nether)。ディメンションごとに別のワールドになる
 * @property x ブロックの X 座標
 * @property y ブロックの Y 座標
 * @property z ブロックの Z 座標
 */
data class TicketLocation(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
)
