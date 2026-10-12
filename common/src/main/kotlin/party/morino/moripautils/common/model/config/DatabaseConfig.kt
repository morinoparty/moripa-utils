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
 * MoripaUtils 全体で共有するデータベースの設定
 *
 * 各機能は同じデータベースの中に機能ごとのテーブル (例: ticket 機能の tickets) を作って使う。
 * [type] で選んだ方のブロック ([sqlite] / [mysql]) だけが使われ、もう一方は無視される。
 * Velocity ではチケットの公開 API だけが使い、Paper と共有できる MySQL の場合にだけ接続する。
 *
 * @property type 接続するデータベースの種類
 * @property sqlite type = sqlite のときの接続設定
 * @property mysql type = mysql のときの接続設定
 */
@Serializable
data class DatabaseConfig(
    val type: DatabaseType = DatabaseType.SQLITE,
    val sqlite: SqliteConfig = SqliteConfig(),
    val mysql: MysqlConfig = MysqlConfig(),
)
