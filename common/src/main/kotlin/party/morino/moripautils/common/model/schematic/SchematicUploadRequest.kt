/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.model.schematic

import java.util.UUID

/**
 * schematic のアップロードの依頼 (info.json に書く情報も含む)
 *
 * @property format schematic の形式
 * @property content schematic のバイト列 ([format] の形式)
 * @property worldSize schematic の範囲の大きさ (content から読み取った値)
 * @property title タイトル (前後の空白を取り除き、空なら null にしておくこと)
 * @property uploaderName アップロードしたプレイヤーの名前
 * @property uploaderUuid アップロードしたプレイヤーの UUID
 * @property spawnPosition schematic を使うときのスポーン位置
 */
data class SchematicUploadRequest(
    val format: SchematicFormat,
    val content: ByteArray,
    val worldSize: SchematicWorldSize,
    val title: String?,
    val uploaderName: String?,
    val uploaderUuid: UUID?,
    val spawnPosition: SchematicSpawnPosition,
) {
    // ByteArray は参照で比較されるため、内容で比較するよう equals / hashCode を定義する
    override fun equals(other: Any?): Boolean = other is SchematicUploadRequest &&
        format == other.format &&
        content.contentEquals(other.content) &&
        worldSize == other.worldSize &&
        title == other.title &&
        uploaderName == other.uploaderName &&
        uploaderUuid == other.uploaderUuid &&
        spawnPosition == other.spawnPosition

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = HASH_MULTIPLIER * result + content.contentHashCode()
        result = HASH_MULTIPLIER * result + worldSize.hashCode()
        result = HASH_MULTIPLIER * result + (title?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + (uploaderName?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + (uploaderUuid?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + spawnPosition.hashCode()
        return result
    }

    private companion object {
        /** hashCode の合成に使う係数 */
        const val HASH_MULTIPLIER: Int = 31
    }
}
