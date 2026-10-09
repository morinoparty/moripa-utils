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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.storage.ObjectStorage

/**
 * [SchematicUploadService] が UUID v7 の id を払い出し、id から決まるキーで保存することを確認するテスト
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

    @Test
    @DisplayName("Uploads to schematics/<uuid v7>.schem and returns the id")
    fun uploadsWithUuidV7Key() = runBlocking {
        val content = byteArrayOf(1, 2, 3)

        val id = service.upload(content)

        // UUID v7 (バージョン 7、RFC 4122 のバリアント) で払い出される
        assertEquals(7, id.version())
        assertEquals(2, id.variant())
        // id から決まるキーに、内容と MIME タイプがそのまま渡される
        val put = storage.puts.single()
        assertEquals("schematics/$id.schem", put.key)
        assertArrayEquals(content, put.content)
        assertEquals(SchematicObjectKey.CONTENT_TYPE, put.contentType)
    }

    @Test
    @DisplayName("Later uploads get larger ids")
    fun idsAreOrderedByUploadTime() = runBlocking {
        val first = service.upload(byteArrayOf())
        val second = service.upload(byteArrayOf())

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
