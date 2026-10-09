/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.model.schematic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ストレージに保存する schematic の形式
 *
 * 形式ごとに info.json の format の値と、schematics/{id}/ に置くファイル名が決まる。
 * Litematica など別の形式に対応する場合は、ここに値を追加し、
 * [party.morino.moripautils.common.schematic.SchematicFormatReader] の実装を用意する。
 * 値の id とファイル名は保存済みの info.json と対応するため、運用開始後は変更しないこと。
 *
 * @property id info.json の format に書く値
 * @property fileName schematics/{id}/ に置くファイル名
 * @property contentType 保存時の MIME タイプ
 */
@Serializable
enum class SchematicFormat(
    val id: String,
    val fileName: String,
    val contentType: String,
) {
    /** Sponge schematic v3 (WorldEdit 7.3 以降 / FAWE の .schem。gzip 圧縮した NBT で、専用の MIME タイプはない) */
    @SerialName("sponge_v3")
    SPONGE_V3("sponge_v3", "schematic.schem", "application/octet-stream"),
}
