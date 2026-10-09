/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.schematic

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.schematic.SchematicFormat
import party.morino.moripautils.common.model.schematic.SchematicSpawnPosition
import party.morino.moripautils.common.model.schematic.SchematicUploadRequest
import party.morino.moripautils.common.model.schematic.SchematicWorldSize
import party.morino.moripautils.common.storage.ObjectStorage
import java.util.UUID

/**
 * [SchematicUploadService] が UUID v7 の id を払い出し、schematics/{id}/ に schematic と info.json を保存することを確認するテスト
 *
 * common のテストには mockk が無いため、ストレージは保存内容を記録するだけの手書きの偽物に差し替える。
 */
class SchematicUploadServiceTest {
    private val storage = RecordingStorage()
    private val service = SchematicUploadService()

    @BeforeEach
    fun setUp() {
        MoripaUtilsKoinContext.start(listOf(module { single<ObjectStorage> { storage } }))
    }

    @AfterEach
    fun tearDown() {
        MoripaUtilsKoinContext.stop()
    }

    /** テスト用の依頼を作る */
    private fun request(content: ByteArray = byteArrayOf(1, 2, 3), title: String? = null) = SchematicUploadRequest(
        format = SchematicFormat.SPONGE_V3,
        content = content,
        worldSize = SchematicWorldSize(3, 4, 5),
        title = title,
        uploaderName = "Steve",
        uploaderUuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        spawnPosition = SchematicSpawnPosition(1.5, 2.0, -3.25, 90f, 10f),
    )

    @Test
    @DisplayName("Uploads schematic and info.json under schematics/<uuid v7>/")
    fun uploadsSchematicAndInfo() = runBlocking {
        val content = byteArrayOf(1, 2, 3)

        val id = service.upload(request(content, title = "House"))

        // UUID v7 (バージョン 7、RFC 4122 のバリアント) で払い出される
        assertEquals(7, id.version())
        assertEquals(2, id.variant())
        // schematic 本体を先に、info.json を後に保存する
        val (schematic, info) = storage.puts
        assertEquals("schematics/$id/schematic.schem", schematic.key)
        assertArrayEquals(content, schematic.content)
        assertEquals("schematics/$id/info.json", info.key)
        assertEquals("application/json", info.contentType)

        // info.json のキーは snake_case で、指定した情報がそのまま書かれる
        val json = Json.parseToJsonElement(info.content.decodeToString()).jsonObject
        assertEquals(id.toString(), json["id"]!!.jsonPrimitive.content)
        assertEquals("sponge_v3", json["format"]!!.jsonPrimitive.content)
        assertEquals("schematic.schem", json["file_name"]!!.jsonPrimitive.content)
        assertEquals("3", json["file_size"]!!.jsonPrimitive.content)
        assertEquals("House", json["title"]!!.jsonPrimitive.content)
        assertEquals("Steve", json["uploader_name"]!!.jsonPrimitive.content)
        assertEquals("-3.25", json["spawn_position"]!!.jsonObject["z"]!!.jsonPrimitive.content)
        assertEquals("4", json["world_size"]!!.jsonObject["height"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("Writes null title explicitly")
    fun writesNullTitle() = runBlocking {
        service.upload(request(title = null))

        // キーを省略せず null を書き、読む側がキーの有無を気にしなくて済むようにする
        val json = Json.parseToJsonElement(storage.puts.last().content.decodeToString()).jsonObject
        assertEquals(JsonNull, json["title"])
    }

    @Test
    @DisplayName("Later uploads get larger ids")
    fun idsAreOrderedByUploadTime() = runBlocking {
        val first = service.upload(request())
        val second = service.upload(request())

        // UUID v7 は単調増加するため、文字列の辞書順でもアップロード順に並ぶ
        assertTrue(first.toString() < second.toString(), "$first < $second")
    }

    /** 保存の呼び出しを記録するだけのストレージ */
    private class RecordingStorage : ObjectStorage {
        /** 保存の 1 回分の呼び出し */
        class Put(val key: String, val content: ByteArray, val contentType: String)

        val puts = mutableListOf<Put>()

        override suspend fun put(key: String, content: ByteArray, contentType: String) {
            puts.add(Put(key, content, contentType))
        }

        override fun close() = Unit
    }
}
