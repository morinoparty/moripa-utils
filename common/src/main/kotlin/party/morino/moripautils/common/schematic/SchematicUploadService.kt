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
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

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
     * id は UUID v7 (先頭 48 bit がミリ秒単位の時刻) なので、id の並びがアップロード順になり、id から時刻も分かる。
     *
     * @param content Sponge schematic (.schem) のバイト列 (形式の検証は呼び出し側で行う)
     * @return アップロードした schematic の id
     * @throws party.morino.moripautils.common.storage.ObjectStorageException アップロードに失敗した場合
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun upload(content: ByteArray): UUID {
        // 標準ライブラリの V7 は同じミリ秒内でも単調増加するため、id の並びがアップロード順と一致する
        val id = Uuid.generateV7().toJavaUuid()
        storage.put(SchematicObjectKey.of(id), content, SchematicObjectKey.CONTENT_TYPE)
        return id
    }
}
