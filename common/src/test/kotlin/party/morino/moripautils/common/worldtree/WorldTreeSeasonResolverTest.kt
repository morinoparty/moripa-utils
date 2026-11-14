/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.worldtree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.moripautils.common.model.config.WorldTreeConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WorldTreeSeasonResolverTest {
    private val config = WorldTreeConfig()

    @Test
    @DisplayName("Maps every month to the default season")
    fun mapsMonthsToDefaultSeasons() {
        val seasons = (1..12).map { WorldTreeSeasonResolver.resolve(config, it).name }

        assertEquals(
            listOf(
                "winter", "winter", "spring", "spring", "summer", "summer",
                "summer", "summer", "summer", "autumn", "autumn", "winter",
            ),
            seasons,
        )
    }

    @Test
    @DisplayName("Uses the configured time zone to determine the month")
    fun usesConfiguredTimeZone() {
        // UTC では 2 月末だが、日本時間では 3 月 1 日
        val clock = Clock.fixed(Instant.parse("2026-02-28T16:00:00Z"), ZoneOffset.UTC)

        assertEquals("spring", WorldTreeSeasonResolver.resolveNow(config, clock).name)
        assertEquals("winter", WorldTreeSeasonResolver.resolveNow(config.copy(timeZone = "UTC"), clock).name)
    }
}
