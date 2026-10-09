/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.model.schematic

import party.morino.moripautils.common.model.schematic.SchematicSpawnPosition

/**
 * schematic として書き出したクリップボード
 *
 * @property content Sponge schematic v3 のバイト列
 * @property spawnPosition 書き出したときのプレイヤーの位置 (クリップボードの基準点からの相対位置)
 */
class ExportedClipboard(
    val content: ByteArray,
    val spawnPosition: SchematicSpawnPosition,
)
