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
 * schematic のアップロードに失敗したことを表す例外
 *
 * @property reason 失敗した理由
 * @param message 失敗の詳細
 */
class SchematicUploadException(
    val reason: SchematicUploadFailure,
    message: String,
) : RuntimeException(message)
