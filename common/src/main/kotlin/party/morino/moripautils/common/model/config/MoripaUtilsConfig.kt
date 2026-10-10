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
 * MoripaUtils 全体の設定 (プラグインのデータフォルダにある config.conf に対応する)
 *
 * Paper / Velocity で同じ形を使う。Velocity ではチケット機能とデータベース / ストレージを使わないため
 * [ticket] / [database] / [storage] は無視される。お知らせは Velocity 専用のため、Paper では [announcement] は無視される。
 *
 * @property server このサーバーを識別する文字列 (例: main, lobby)。メトリクスのラベルやチケットの送信元として使う。
 *   英数字 / ハイフン / アンダースコアのみ使用できる
 * @property database 各機能が共有するデータベース (SQLite / MySQL) の設定
 * @property storage 各機能が共有するオブジェクトストレージ (S3 互換) の設定
 * @property observability メトリクス公開 (observability 機能) の設定
 * @property ticket お問い合わせ (ticket 機能) の設定
 * @property announcement 定期的なお知らせ (announcement 機能) の設定
 * @throws IllegalArgumentException server が空、または使用できない文字を含む場合
 */
@Serializable
data class MoripaUtilsConfig(
    val server: String = DEFAULT_SERVER,
    val database: DatabaseConfig = DatabaseConfig(),
    val storage: StorageConfig = StorageConfig(),
    val observability: ObservabilityConfig = ObservabilityConfig(),
    val ticket: TicketConfig = TicketConfig(),
    val announcement: AnnouncementConfig = AnnouncementConfig(),
) {
    init {
        // ラベル値やファイル名にも使うため、記号や空白を含まない識別子に限定する
        // kotlinx.serialization はデコード時にも init ブロックを実行するので、config.conf の値もここで検証される
        require(SERVER_PATTERN.matches(server)) {
            "server must match ${SERVER_PATTERN.pattern}, but was '$server'"
        }
    }

    companion object {
        /** server の既定値 */
        const val DEFAULT_SERVER: String = "main"

        /** server に使用できる文字のパターン (空文字は 1 文字以上の指定で弾く) */
        val SERVER_PATTERN: Regex = Regex("^[A-Za-z0-9_-]+$")
    }
}
