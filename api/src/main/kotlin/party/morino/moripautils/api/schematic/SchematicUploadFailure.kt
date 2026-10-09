/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.api.schematic

/**
 * schematic のアップロードに失敗した理由
 */
enum class SchematicUploadFailure {
    /** WorldEdit / FAWE が導入されていない、または API に互換性がない (クリップボードのアップロードのみ) */
    WORLDEDIT_UNAVAILABLE,

    /** config.conf の storage が設定されていない (bucket / 認証情報が空) */
    STORAGE_NOT_CONFIGURED,

    /** プレイヤーのクリップボードが空 */
    EMPTY_CLIPBOARD,

    /** 渡された内容が Sponge schematic v3 として読めない */
    INVALID_SCHEMATIC,

    /** schematic の書き出し、またはストレージへの保存に失敗した */
    UPLOAD_FAILED,
}
