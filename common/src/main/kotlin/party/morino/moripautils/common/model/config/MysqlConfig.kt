/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.config

import kotlinx.serialization.Serializable

/**
 * MySQL の接続設定 (database.type = mysql のときに使う)
 *
 * JDBC ドライバー (mysql-connector-j) は Paper 本体に同梱されているものを使う。
 *
 * @property host 接続先のホスト名 / アドレス
 * @property port 接続先のポート番号
 * @property database 使用するデータベース (スキーマ) 名。事前に作成しておくこと
 * @property user 接続ユーザー名
 * @property password 接続パスワード
 * @property properties JDBC URL に付け加える接続プロパティ (例: sslMode = DISABLED)
 * @throws IllegalArgumentException host / database / user が空、または port が範囲外の場合
 */
@Serializable
data class MysqlConfig(
    val host: String = "localhost",
    val port: Int = DEFAULT_PORT,
    val database: String = "moripa_utils",
    val user: String = "root",
    val password: String = "",
    val properties: Map<String, String> = emptyMap(),
) {
    init {
        // 接続先が決まらない設定は起動時に失敗させ、接続時の分かりにくいエラーを避ける
        require(host.isNotBlank()) { "database.mysql.host must not be blank" }
        require(port in 1..MAX_PORT) { "database.mysql.port must be in 1.., but was " }
        require(database.isNotBlank()) { "database.mysql.database must not be blank" }
        require(user.isNotBlank()) { "database.mysql.user must not be blank" }
    }

    companion object {
        /** MySQL の既定ポート */
        const val DEFAULT_PORT: Int = 3306

        /** ポート番号の上限 */
        private const val MAX_PORT: Int = 65535
    }
}
