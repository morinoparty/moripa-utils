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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * お知らせ (定期的に全プレイヤーへ送るメッセージ) の設定
 *
 * Velocity 専用の機能で、メッセージの本文は設定ファイルではなく message ディレクトリの JSON ファイルに書く。
 *
 * @property enabled お知らせを送るかどうか
 * @property interval お知らせを 1 件ずつ送る間隔 (HOCON の期間の書式。例: "30 minutes")。1 秒未満を指定すると設定の読み込み時に失敗する
 * @throws IllegalArgumentException interval が 1 秒未満の場合
 */
@Serializable
data class AnnouncementConfig(
    val enabled: Boolean = true,
    val interval: Duration = DEFAULT_INTERVAL,
) {
    init {
        // 短すぎる間隔はチャットを埋めてしまうため、設定の読み込み時に失敗させる (0 以下はスケジューラーにも渡せない)
        require(interval >= MIN_INTERVAL) { "announcement.interval must be at least $MIN_INTERVAL, but was $interval" }
    }

    companion object {
        /** interval の既定値 */
        val DEFAULT_INTERVAL: Duration = 30.minutes

        /** interval に指定できる最小の間隔 */
        val MIN_INTERVAL: Duration = 1.seconds
    }
}
