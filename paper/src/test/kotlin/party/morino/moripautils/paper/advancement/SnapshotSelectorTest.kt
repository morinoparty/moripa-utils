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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.moripautils.paper.model.advancement.AdvancementSnapshotSummary
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class SnapshotSelectorTest {
    private val newest = summary(1, "00000000-0000-0000-0000-000000000001", pinned = false, count = 120)
    private val older = summary(2, "00000000-0000-0000-0000-000000000002", pinned = true, count = null)
    private val snapshots = listOf(newest, older)

    @Test
    @DisplayName("A positive integer selects the snapshot by its newest-first index")
    fun indexSelectsByPosition() {
        val selector = SnapshotSelector.parse("2")

        assertEquals(SnapshotSelector.ByIndex(2), selector)
        assertEquals(older, selector?.select(snapshots))
    }

    @Test
    @DisplayName("A UUID selects the snapshot with the same id")
    fun uuidSelectsById() {
        val selector = SnapshotSelector.parse(newest.id.toString())

        assertEquals(newest, selector?.select(snapshots))
    }

    @Test
    @DisplayName("Zero, negative numbers and other text are rejected")
    fun invalidInputIsRejected() {
        assertNull(SnapshotSelector.parse("0"))
        assertNull(SnapshotSelector.parse("-1"))
        assertNull(SnapshotSelector.parse("latest"))
    }

    @Test
    @DisplayName("An index past the end selects nothing")
    fun indexOutOfRangeSelectsNothing() {
        assertNull(SnapshotSelector.ByIndex(3).select(snapshots))
    }

    @Test
    @DisplayName("The tooltip shows the timestamp, advancement count, save cause, pin and short id")
    fun formatterDescribesSnapshot() {
        val formatter = AdvancementSnapshotFormatter(ZoneId.of("Asia/Tokyo"))

        assertEquals("2026-11-14 12:03 / 進捗 120 / DISCONNECT / 00000000", formatter.describe(newest))
        assertEquals("2026-11-14 12:03 / 進捗なし / DISCONNECT / 固定 / 00000000", formatter.describe(older))
    }

    private fun summary(index: Int, id: String, pinned: Boolean, count: Int?) = AdvancementSnapshotSummary(
        index = index,
        id = UUID.fromString(id),
        timestamp = OffsetDateTime.of(2026, 11, 14, 3, 3, 0, 0, ZoneOffset.UTC),
        saveCause = "DISCONNECT",
        pinned = pinned,
        advancementCount = count,
    )
}
