/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.schematic

import party.morino.moripautils.common.model.schematic.SchematicFormat
import party.morino.moripautils.common.model.schematic.SchematicWorldSize

/**
 * schematic のバイト列から、info.json に書く情報を読み取る (形式ごとに実装する)
 *
 * アップロードの前に形式の検証を兼ねて呼び、読み取れない場合は不正な schematic として拒否する。
 */
interface SchematicFormatReader {
    /** 読み取る形式 */
    val format: SchematicFormat

    /**
     * 範囲の大きさを読み取る
     *
     * @param content schematic のバイト列
     * @return 範囲の大きさ。この形式として読めない場合は null
     */
    fun readWorldSize(content: ByteArray): SchematicWorldSize?
}
