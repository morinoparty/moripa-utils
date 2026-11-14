/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.config

import kotlinx.serialization.Serializable

/**
 * ワールド上のブロックの座標 (範囲の角などを設定ファイルに書くために使う)
 *
 * @property x X 座標
 * @property y Y 座標
 * @property z Z 座標
 */
@Serializable
data class BlockPosition(
    val x: Int,
    val y: Int,
    val z: Int,
)
