/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.schematic

import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.storage.ObjectStorage
import party.morino.moripautils.common.uuid.UuidV7
import java.util.UUID

/**
 * schematic をオブジェクトストレージへアップロードするサービス
 *
 * WorldEdit への依存はプラットフォーム側 (Paper) に閉じ込め、ここでは書き出し済みのバイト列だけを扱う。
 */
class SchematicUploadService : MoripaUtilsKoinComponent {
    private val storage: ObjectStorage by inject()

    /**
     * schematic をアップロードし、払い出した id を返す
     *
     * id は UUID v7 ([UuidV7]) なので、id の並びがおおよそアップロード順になり、id から時刻も分かる。
     *
     * @param content Sponge schematic (.schem) のバイト列 (形式の検証は呼び出し側で行う)
     * @return アップロードした schematic の id
     * @throws party.morino.moripautils.common.storage.ObjectStorageException アップロードに失敗した場合
     */
    suspend fun upload(content: ByteArray): UUID {
        // 時刻順に並ぶ id にするため UUID v7 を使う
        val id = UuidV7.generate()
        storage.put(SchematicObjectKey.of(id), content, SchematicObjectKey.CONTENT_TYPE)
        return id
    }
}
