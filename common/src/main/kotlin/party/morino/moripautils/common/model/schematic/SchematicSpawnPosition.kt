/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.model.schematic

import kotlinx.serialization.Serializable

/**
 * schematic を使うときのスポーン位置 (info.json の spawn_position)
 *
 * 座標は schematic の貼り付け基準点 (WorldEdit で //copy したときの基準点。Sponge schematic の Offset の基準) からの相対位置で、
 * 貼り付けた位置にこの値を足すとワールド上のスポーン位置になる (hollow-cube/schem の forEachBlock と同じ座標系)。
 *
 * @property x 基準点からの相対 X 座標
 * @property y 基準点からの相対 Y 座標
 * @property z 基準点からの相対 Z 座標
 * @property yaw 水平方向の向き (度)
 * @property pitch 垂直方向の向き (度)
 */
@Serializable
data class SchematicSpawnPosition(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
) {
    companion object {
        /** 位置が分からない場合に使う値 (基準点そのもの、向きは 0) */
        val ORIGIN: SchematicSpawnPosition = SchematicSpawnPosition(0.0, 0.0, 0.0, 0f, 0f)
    }
}
