/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.worldtree

import party.morino.moripautils.common.model.config.WorldTreeConfig
import party.morino.moripautils.common.model.config.WorldTreeSeason
import java.time.Clock
import java.time.LocalDate

/**
 * 月から世界樹の季節を決める
 */
object WorldTreeSeasonResolver {
    /**
     * 指定した月の季節を返す
     *
     * 設定の検証 ([WorldTreeConfig] の init ブロック) で 1 月から 12 月がちょうど 1 回ずつ割り当てられていることを
     * 保証しているため、範囲内の月なら必ず見つかる。
     *
     * @param config 世界樹の設定
     * @param month 月 (1 から 12)
     * @return その月の季節
     * @throws IllegalArgumentException month に割り当てられた季節が無い場合 (範囲外の月など)
     */
    fun resolve(config: WorldTreeConfig, month: Int): WorldTreeSeason =
        requireNotNull(config.seasons.firstOrNull { month in it.months }) { "No worldTree season is assigned to month $month" }

    /**
     * 現在の月の季節を返す
     *
     * 月はサーバーの既定のタイムゾーンではなく、設定のタイムゾーン ([WorldTreeConfig.timeZone]) で求める。
     *
     * @param config 世界樹の設定
     * @param clock 現在時刻を取得する時計 (テストで差し替えられるようにする)
     * @return 現在の月の季節
     */
    fun resolveNow(config: WorldTreeConfig, clock: Clock = Clock.systemUTC()): WorldTreeSeason =
        resolve(config, LocalDate.now(clock.withZone(config.zoneId)).monthValue)
}
