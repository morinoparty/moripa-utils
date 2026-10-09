/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.model.schematic

import party.morino.moripautils.api.schematic.SchematicUploadFailure
import java.util.UUID

/**
 * schematic のアップロードの結果
 */
sealed interface SchematicUploadResult {
    /**
     * アップロードに成功した
     *
     * @property id 払い出した schematic の id (UUID v7)
     */
    data class Success(val id: UUID) : SchematicUploadResult

    /**
     * アップロードに失敗した
     *
     * @property reason 失敗した理由
     * @property message ログや API の例外に使う詳細 (英語)
     */
    data class Failure(val reason: SchematicUploadFailure, val message: String) : SchematicUploadResult
}
