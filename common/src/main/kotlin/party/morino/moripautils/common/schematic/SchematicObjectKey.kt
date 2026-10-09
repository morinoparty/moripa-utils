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
import java.util.UUID

/**
 * schematic をオブジェクトストレージに保存するときのキーを決める
 *
 * schematic ごとに schematics/{id}/ というディレクトリを作り、schematic 本体 (ファイル名は形式ごとに決まる) と
 * 情報 (info.json) を並べて置く。
 * キーの形式を変えると保存済みの schematic を id から引けなくなるため、運用開始後は変更しないこと。
 */
object SchematicObjectKey {
    /** schematic を保存するキーの接頭辞 (他の機能のオブジェクトと混ざらないようにする) */
    const val PREFIX: String = "schematics/"

    /** 情報のファイル名 */
    const val INFO_FILE_NAME: String = "info.json"

    /** 情報の MIME タイプ */
    const val INFO_CONTENT_TYPE: String = "application/json"

    /**
     * id と形式から schematic 本体の保存先のキーを作る
     *
     * @param id schematic の id
     * @param format schematic の形式
     * @return 保存先のキー (例: schematics/0192b3c4-.../schematic.schem)
     */
    fun schematic(id: UUID, format: SchematicFormat): String = "$PREFIX$id/${format.fileName}"

    /**
     * id から情報の保存先のキーを作る
     *
     * @param id schematic の id
     * @return 保存先のキー (例: schematics/0192b3c4-.../info.json)
     */
    fun info(id: UUID): String = "$PREFIX$id/$INFO_FILE_NAME"
}
