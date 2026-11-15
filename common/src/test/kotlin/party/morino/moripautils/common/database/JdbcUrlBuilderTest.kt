/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.moripautils.common.model.config.DatabaseConfig
import party.morino.moripautils.common.model.config.DatabaseType
import party.morino.moripautils.common.model.config.MysqlConfig
import party.morino.moripautils.common.model.config.SqliteConfig
import java.nio.file.Path

/**
 * [JdbcUrlBuilder] が設定の種類ごとに正しい URL を組み立てるかを確認するテスト
 */
class JdbcUrlBuilderTest {
    private val dataDirectory: Path = Path.of("/srv/plugins/MoripaUtils")

    @Test
    @DisplayName("Resolves the SQLite file against the data directory")
    fun buildsSqliteUrl() {
        val config = DatabaseConfig(sqlite = SqliteConfig(file = "db/moripa.db"))

        assertEquals("jdbc:sqlite:${dataDirectory.resolve("db/moripa.db").toAbsolutePath()}", JdbcUrlBuilder.build(config, dataDirectory))
    }

    @Test
    @DisplayName("Builds a MySQL URL with encoded properties")
    fun buildsMysqlUrl() {
        val mysql = MysqlConfig(host = "db", port = 3307, database = "moripa", properties = mapOf("sslMode" to "DISABLED", "a" to "b&c"))
        val withProperties = DatabaseConfig(type = DatabaseType.MYSQL, mysql = mysql)
        val withoutProperties = DatabaseConfig(type = DatabaseType.MYSQL, mysql = mysql.copy(properties = emptyMap()))

        // プロパティの値に含まれる & は URL を壊さないようにエンコードされる
        assertEquals("jdbc:mysql://db:3307/moripa?sslMode=DISABLED&a=b%26c", JdbcUrlBuilder.build(withProperties, dataDirectory))
        assertEquals("jdbc:mysql://db:3307/moripa", JdbcUrlBuilder.build(withoutProperties, dataDirectory))
    }
}
