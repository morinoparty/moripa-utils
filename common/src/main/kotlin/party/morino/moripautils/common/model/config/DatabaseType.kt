/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 接続するデータベースの種類
 *
 * config.conf には小文字の名前 (sqlite / mysql) で記述する。
 */
@Serializable
enum class DatabaseType {
    /** プラグインのデータフォルダに置く SQLite ファイル (既定。追加のサーバーが不要) */
    @SerialName("sqlite")
    SQLITE,

    /** 外部の MySQL サーバー (複数の Paper サーバーで同じデータを共有できる) */
    @SerialName("mysql")
    MYSQL,
}
