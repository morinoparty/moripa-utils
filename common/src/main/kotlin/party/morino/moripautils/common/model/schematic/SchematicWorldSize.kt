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
 * schematic の範囲の大きさ (info.json の world_size。Sponge schematic の Width / Height / Length)
 *
 * @property width X 方向のブロック数
 * @property height Y 方向のブロック数
 * @property length Z 方向のブロック数
 */
@Serializable
data class SchematicWorldSize(
    val width: Int,
    val height: Int,
    val length: Int,
)
