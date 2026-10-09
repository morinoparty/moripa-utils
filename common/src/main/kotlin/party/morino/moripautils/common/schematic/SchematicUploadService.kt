/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.schematic

import kotlinx.serialization.json.Json
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.MoripaUtilsConfig
import party.morino.moripautils.common.model.schematic.SchematicInfo
import party.morino.moripautils.common.model.schematic.SchematicUploadRequest
import party.morino.moripautils.common.storage.ObjectStorage
import java.time.Instant
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * schematic をオブジェクトストレージへアップロードするサービス
 *
 * WorldEdit への依存はプラットフォーム側 (Paper) に閉じ込め、ここでは書き出し済みのバイト列と情報だけを扱う。
 * schematic ごとに schematics/{id}/ へ schematic 本体 (ファイル名は形式ごとに決まる) と情報 (info.json) を保存する。
 */
class SchematicUploadService : MoripaUtilsKoinComponent {
    private val storage: ObjectStorage by inject()
    private val config: MoripaUtilsConfig by inject()

    /**
     * schematic と情報をアップロードし、払い出した id を返す
     *
     * id は UUID v7 (先頭 48 bit がミリ秒単位の時刻) なので、id の並びがアップロード順になり、id から時刻も分かる。
     * info.json は schematic 本体の保存に成功してから書くため、info.json があれば schematic 本体も揃っている。
     *
     * @param request アップロードする schematic と情報 (形式の検証は呼び出し側で行う)
     * @return アップロードした schematic の id
     * @throws party.morino.moripautils.common.storage.ObjectStorageException アップロードに失敗した場合
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun upload(request: SchematicUploadRequest): UUID {
        // 標準ライブラリの V7 は同じミリ秒内でも単調増加するため、id の並びがアップロード順と一致する
        val id = Uuid.generateV7().toJavaUuid()
        storage.put(SchematicObjectKey.schematic(id, request.format), request.content, request.format.contentType)
        val info = createInfo(id, request, Instant.now())
        storage.put(
            SchematicObjectKey.info(id),
            json.encodeToString(SchematicInfo.serializer(), info).toByteArray(Charsets.UTF_8),
            SchematicObjectKey.INFO_CONTENT_TYPE,
        )
        return id
    }

    /**
     * 保存する情報を組み立てる
     *
     * @param id schematic の id
     * @param request アップロードの依頼
     * @param uploadedAt アップロードした日時
     * @return info.json に書く情報
     */
    private fun createInfo(id: UUID, request: SchematicUploadRequest, uploadedAt: Instant): SchematicInfo = SchematicInfo(
        id = id.toString(),
        format = request.format,
        fileName = request.format.fileName,
        fileSize = request.content.size.toLong(),
        title = request.title,
        uploaderName = request.uploaderName,
        uploaderUuid = request.uploaderUuid?.toString(),
        server = config.server,
        spawnPosition = request.spawnPosition,
        worldSize = request.worldSize,
        uploadedAt = uploadedAt.toString(),
    )

    private companion object {
        /** info.json の書き出しに使う JSON 設定 (null の項目もキーを省略せずに書き、人が読みやすいよう整形する) */
        val json: Json = Json {
            prettyPrint = true
            explicitNulls = true
            encodeDefaults = true
        }
    }
}
