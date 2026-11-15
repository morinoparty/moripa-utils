/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.database

import party.morino.moripautils.common.model.config.DatabaseConfig
import party.morino.moripautils.common.model.config.DatabaseType
import party.morino.moripautils.common.model.config.MysqlConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * 設定から JDBC の接続 URL を組み立てる (外部状態に依存しない純粋関数の集まり)
 */
object JdbcUrlBuilder {
    /**
     * 設定された種類のデータベースへ接続する JDBC URL を組み立てる
     *
     * @param config データベースの設定
     * @param dataDirectory プラグインのデータフォルダ (SQLite の相対パスの基準になる)
     * @return JDBC URL
     */
    fun build(config: DatabaseConfig, dataDirectory: Path): String = when (config.type) {
        DatabaseType.SQLITE -> "jdbc:sqlite:${sqliteFile(config, dataDirectory)}"
        DatabaseType.MYSQL -> mysql(config.mysql)
    }

    /**
     * SQLite のデータベースファイルの絶対パスを求める
     *
     * @param config データベースの設定
     * @param dataDirectory プラグインのデータフォルダ
     * @return データベースファイルの絶対パス (相対パスはデータフォルダからの相対として解決する)
     */
    fun sqliteFile(config: DatabaseConfig, dataDirectory: Path): Path =
        dataDirectory.resolve(config.sqlite.file).toAbsolutePath()

    /**
     * MySQL の JDBC URL を組み立てる
     *
     * @param config MySQL の接続設定
     * @return jdbc:mysql://host:port/database?key=value の形の URL (プロパティが無ければ ? 以降を付けない)
     */
    private fun mysql(config: MysqlConfig): String {
        val base = "jdbc:mysql://${config.host}:${config.port}/${config.database}"
        if (config.properties.isEmpty()) return base
        // 値に & や = が含まれても URL が壊れないようにエンコードする
        val query = config.properties.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$base?$query"
    }

    /**
     * URL のクエリ文字列に使えるようにエンコードする
     *
     * @param value エンコードする文字列
     * @return UTF-8 でエンコードした文字列
     */
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
