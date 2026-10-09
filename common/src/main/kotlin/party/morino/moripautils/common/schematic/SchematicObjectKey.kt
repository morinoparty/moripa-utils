/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.schematic

import java.util.UUID

/**
 * schematic をオブジェクトストレージに保存するときのキーを決める
 *
 * キーの形式を変えると保存済みの schematic を id から引けなくなるため、運用開始後は変更しないこと。
 */
object SchematicObjectKey {
    /** schematic を保存するキーの接頭辞 (他の機能のオブジェクトと混ざらないようにする) */
    const val PREFIX: String = "schematics/"

    /** Sponge schematic のファイル拡張子 (WorldEdit / FAWE の //schem load で読める形式) */
    const val EXTENSION: String = "schem"

    /** 保存時の MIME タイプ (Sponge schematic は gzip 圧縮した NBT で、専用の MIME タイプはない) */
    const val CONTENT_TYPE: String = "application/octet-stream"

    /**
     * id から保存先のキーを作る
     *
     * @param id schematic の id
     * @return 保存先のキー (例: schematics/0192b3c4-....schem)
     */
    fun of(id: UUID): String = "$PREFIX$id.$EXTENSION"
}
