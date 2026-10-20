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
 * SQLite の接続設定 (database.type = sqlite のときに使う)
 *
 * @property file データベースファイル名 (プラグインのデータフォルダからの相対パス。絶対パスも指定できる)
 * @throws IllegalArgumentException file が空の場合
 */
@Serializable
data class SqliteConfig(
    val file: String = DEFAULT_FILE,
) {
    init {
        // 空文字だとデータフォルダ自体を開こうとしてしまうため弾く
        require(file.isNotBlank()) { "database.sqlite.file must not be blank" }
    }

    companion object {
        /** file の既定値 */
        const val DEFAULT_FILE: String = "moripa-utils.db"
    }
}
