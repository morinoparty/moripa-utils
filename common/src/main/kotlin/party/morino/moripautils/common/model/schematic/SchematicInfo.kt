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
 * schematic と一緒にストレージへ保存する情報 (schematics/{id}/info.json)
 *
 * 外部のツールから読まれるため、JSON のキーは snake_case にする。キー名を変えると既存の info.json と互換がなくなる。
 *
 * @property id schematic の id (UUID v7)
 * @property format schematic の形式 (JSON では sponge_v3 などの文字列になる)
 * @property fileName 同じディレクトリにある schematic のファイル名 (形式ごとに決まる)
 * @property fileSize schematic のファイルサイズ (バイト)
 * @property title タイトル (指定されなかった場合は null)
 * @property uploaderName アップロードしたプレイヤーの名前 (分からない場合は null)
 * @property uploaderUuid アップロードしたプレイヤーの UUID (分からない場合は null)
 * @property spawnPosition schematic を使うときのスポーン位置
 * @property worldSize schematic の範囲の大きさ
 * @property uploadedAt アップロードした日時 (ISO 8601、UTC)
 */
@Serializable
data class SchematicInfo(
    val id: String,
    val format: SchematicFormat,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size") val fileSize: Long,
    val title: String?,
    @SerialName("uploader_name") val uploaderName: String?,
    @SerialName("uploader_uuid") val uploaderUuid: String?,
    @SerialName("spawn_position") val spawnPosition: SchematicSpawnPosition,
    @SerialName("world_size") val worldSize: SchematicWorldSize,
    @SerialName("uploaded_at") val uploadedAt: String,
) {
    companion object {
        /** title の最大文字数 */
        const val MAX_TITLE_LENGTH: Int = 100
    }
}
