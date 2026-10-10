/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import party.morino.moripautils.common.model.config.DatabaseType
import party.morino.moripautils.common.model.config.HttpServerConfig
import party.morino.moripautils.common.model.config.MoripaUtilsConfig
import party.morino.moripautils.common.model.config.ObservabilityConfig
import party.morino.moripautils.common.model.config.TicketCategory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class MoripaUtilsConfigLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    /** テスト用の設定ファイルを書き出してローダーを返す */
    private fun loaderWith(text: String): MoripaUtilsConfigLoader {
        Files.writeString(tempDir.resolve(MoripaUtilsConfigLoader.CONFIG_FILE_NAME), text)
        return MoripaUtilsConfigLoader(tempDir, MoripaUtilsConfig())
    }

    @Test
    @DisplayName("Writes defaults as HOCON and reloads them")
    fun writesDefaultsAndReloads() {
        // データフォルダ自体がまだ存在しない状態から読み込む
        val dataDirectory = tempDir.resolve("plugin")
        val defaults = MoripaUtilsConfig(
            server = "proxy",
            observability = ObservabilityConfig(http = HttpServerConfig(port = 9226)),
        )
        val loader = MoripaUtilsConfigLoader(dataDirectory, defaults)

        // 初回は既定値がそのまま返り、ファイルが書き出される
        assertEquals(defaults, loader.load())
        val text = Files.readString(dataDirectory.resolve(MoripaUtilsConfigLoader.CONFIG_FILE_NAME))
        // JSON ではなく HOCON (key=value 形式) で書き出されている
        assertFalse(text.trimStart().startsWith("{"), text)
        assertTrue(text.contains("port=9226"), text)

        // 書き出したファイルを読み直しても同じ設定になる
        assertEquals(defaults, loader.load())
    }

    @Test
    @DisplayName("Parses custom values and fills omitted keys with defaults")
    fun parsesCustomValues() {
        val loader = loaderWith(
            """
            server = lobby
            observability {
              enabled = false
              http { port = 1234 }
            }
            ticket {
              categories = [ { id = "report", name = "通報" } ]
              webhook { url = "https://example.com/hook" }
            }
            """.trimIndent(),
        )

        val loaded = loader.load()

        // 指定したキーは反映され、省略したキーは既定値になる
        assertEquals("lobby", loaded.server)
        assertEquals(false, loaded.observability.enabled)
        assertEquals(1234, loaded.observability.http.port)
        assertEquals("/metrics", loaded.observability.http.path)
        assertEquals(20L, loaded.observability.metrics.samplingIntervalTicks)
        assertEquals(listOf(TicketCategory("report", "通報")), loaded.ticket.categories)
        assertEquals("https://example.com/hook", loaded.ticket.webhook.url)
        assertEquals(DatabaseType.SQLITE, loaded.database.type)
        assertEquals("moripa-utils.db", loaded.database.sqlite.file)
    }

    @Test
    @DisplayName("Parses MySQL database settings")
    fun parsesMysqlDatabase() {
        val loader = loaderWith(
            """
            database {
              type = mysql
              mysql {
                host = db.example.com
                user = moripa
                password = secret
                properties { sslMode = DISABLED }
              }
            }
            """.trimIndent(),
        )

        val database = loader.load().database

        // 小文字の type が enum に対応し、省略したポートなどは既定値になる
        assertEquals(DatabaseType.MYSQL, database.type)
        assertEquals("db.example.com", database.mysql.host)
        assertEquals(3306, database.mysql.port)
        assertEquals("moripa", database.mysql.user)
        assertEquals(mapOf("sslMode" to "DISABLED"), database.mysql.properties)
    }

    @Test
    @DisplayName("Parses S3 compatible storage settings")
    fun parsesStorage() {
        val loader = loaderWith(
            """
            storage {
              endpoint = "https://example.r2.cloudflarestorage.com"
              region = auto
              bucket = moripa
              accessKeyId = key
              secretAccessKey = s3cr3t-value
            }
            """.trimIndent(),
        )

        val storage = loader.load().storage

        // 指定した値が反映され、省略した pathStyleAccess は既定値になる
        assertEquals("https://example.r2.cloudflarestorage.com", storage.endpoint)
        assertEquals("auto", storage.region)
        assertEquals("moripa", storage.bucket)
        assertEquals(false, storage.pathStyleAccess)
        assertTrue(storage.isConfigured)
        // シークレットは toString に出さない
        assertFalse(storage.toString().contains("s3cr3t-value"), storage.toString())
    }

    @Test
    @DisplayName("Rejects storage endpoint without scheme")
    fun rejectsStorageEndpointWithoutScheme() {
        val loader = loaderWith(
            """
            storage { endpoint = "s3.example.com" }
            """.trimIndent(),
        )

        assertThrows(IllegalStateException::class.java) { loader.load() }
    }

    @Test
    @DisplayName("Rejects invalid ticket category id")
    fun rejectsInvalidCategoryId() {
        // 大文字や空白を含む id は TicketCategory の init ブロックで拒否される
        val loader = loaderWith(
            """
            ticket { categories = [ { id = "Bad Id", name = "不正" } ] }
            """.trimIndent(),
        )

        // init ブロックの例外はローダーが設定ファイルのパス付き IllegalStateException に包み直す
        val exception = assertThrows(IllegalStateException::class.java) { loader.load() }
        assertTrue(exception.message!!.contains(MoripaUtilsConfigLoader.CONFIG_FILE_NAME), exception.message)
    }

    @Test
    @DisplayName("Parses announcement interval as HOCON duration")
    fun parsesAnnouncementInterval() {
        val loader = loaderWith(
            """
            announcement { interval = 10 minutes }
            """.trimIndent(),
        )

        assertEquals(10.minutes, loader.load().announcement.interval)
    }

    @Test
    @DisplayName("Rejects too short announcement interval")
    fun rejectsTooShortAnnouncementInterval() {
        val loader = loaderWith(
            """
            announcement { interval = 500 milliseconds }
            """.trimIndent(),
        )

        assertThrows(IllegalStateException::class.java) { loader.load() }
    }
}
