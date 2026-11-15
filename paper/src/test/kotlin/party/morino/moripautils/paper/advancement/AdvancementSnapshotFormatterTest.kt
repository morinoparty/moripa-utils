/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.advancement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.moripautils.paper.model.advancement.AdvancementSnapshotSummary
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class AdvancementSnapshotFormatterTest {
    private val formatter = AdvancementSnapshotFormatter(ZoneOffset.ofHours(9))

    private val summary = AdvancementSnapshotSummary(
        index = 3,
        id = UUID.fromString("1a2b3c4d-0000-0000-0000-000000000000"),
        timestamp = OffsetDateTime.of(2026, 11, 14, 3, 4, 0, 0, ZoneOffset.UTC),
        saveCause = "DISCONNECT",
        pinned = true,
        advancementCount = 123,
    )

    @Test
    @DisplayName("The tooltip leads with the newest-first index and omits the id shown as the suggestion")
    fun tooltipLeadsWithIndex() {
        assertEquals("#3 2026-11-14 12:04 / 進捗 123 / DISCONNECT / 固定", formatter.tooltip(summary))
    }

    @Test
    @DisplayName("The tooltip says there is no advancement data when the count is unknown")
    fun tooltipWithoutAdvancementData() {
        assertEquals(
            "#3 2026-11-14 12:04 / 進捗なし / DISCONNECT",
            formatter.tooltip(summary.copy(pinned = false, advancementCount = null)),
        )
    }
}
