/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.moripautils.api.model.ticket.Ticket
import party.morino.moripautils.api.model.ticket.TicketCloseReason
import party.morino.moripautils.api.model.ticket.TicketLocation
import party.morino.moripautils.api.model.ticket.TicketStatus
import party.morino.moripautils.common.model.config.TicketConfig
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TicketSummaryTextTest {
    private val ticket = Ticket(
        id = 42,
        serverId = "lobby",
        playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        playerName = "Steve",
        location = TicketLocation("world", 0, 64, 0),
        categoryId = "grief",
        content = "  chest\n\nwas   stolen  ",
        status = TicketStatus.CLOSED,
        createdAt = Instant.parse("2026-11-14T03:04:00Z"),
        closeReason = TicketCloseReason.DONE,
    )

    @Test
    @DisplayName("The tooltip shows the status, category, date, sender and a flattened preview")
    fun tooltipSummarizesTicket() {
        val tooltip = TicketSummaryText.tooltip(ticket, "荒らし、盗難について", ZoneOffset.ofHours(9))

        assertEquals("[クローズ (対応完了)] 荒らし、盗難について 2026/11/14 12:04 Steve: chest was stolen", tooltip)
    }

    @Test
    @DisplayName("A long preview is cut at the limit with an ellipsis")
    fun longPreviewIsTruncated() {
        val preview = TicketSummaryText.preview("a".repeat(TicketSummaryText.PREVIEW_LENGTH + 5))

        assertEquals("a".repeat(TicketSummaryText.PREVIEW_LENGTH) + "…", preview)
    }

    @Test
    @DisplayName("The category name falls back to the id when the category was removed from the config")
    fun categoryNameFallsBackToId() {
        assertEquals("荒らし、盗難について", TicketSummaryText.categoryName(TicketConfig(), ticket))
        assertEquals("removed", TicketSummaryText.categoryName(TicketConfig(), ticket.copy(categoryId = "removed")))
    }
}
