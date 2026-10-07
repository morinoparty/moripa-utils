/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.ticket.webhook

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.moripautils.common.model.config.TicketCategory
import party.morino.moripautils.common.model.config.TicketWebhookConfig
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketActor
import party.morino.moripautils.common.model.ticket.TicketCloseReason
import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketCommentAuthorType
import party.morino.moripautils.common.model.ticket.TicketLocation
import party.morino.moripautils.common.model.ticket.TicketStatus
import party.morino.moripautils.common.model.ticket.TicketStatusChange
import java.time.Instant
import java.util.UUID

/**
 * Discord Webhook の本文 (Embed) とアイコン URL の組み立てを検証する
 */
class DiscordWebhookPayloadTest {
    private val playerUuid = UUID.fromString("f8b761ec-4a54-48eb-a040-c5604042bcc9")

    private val ticket = Ticket(
        id = 118,
        serverId = "main",
        playerUuid = playerUuid,
        playerName = "_NIKOMARU",
        location = TicketLocation("world_nether", 415, 32, 362),
        categoryId = "grief",
        content = "荒らし被害の報告",
        status = TicketStatus.OPEN,
        createdAt = Instant.parse("2026-08-18T12:58:00Z"),
    )

    @Test
    @DisplayName("Builds the ticket embed with author, info block, message and footer")
    fun buildsTicketEmbed() {
        val payload = DiscordWebhookPayload.create(
            ticket,
            TicketCategory("grief", "荒らし、盗難について"),
            TicketWebhookConfig(),
        )

        val embed = payload["embeds"]!!.jsonArray.single().jsonObject
        val avatarUrl = "https://api.mcheads.org/head/$playerUuid/64"
        assertEquals("Ticket Created - #118", embed["title"]!!.jsonPrimitive.content)
        assertEquals("_NIKOMARU", embed["author"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(avatarUrl, embed["author"]!!.jsonObject["icon_url"]!!.jsonPrimitive.content)
        assertEquals(
            """
            ```yaml
            UUID: $playerUuid
            Server: main
            World: world_nether
            Location: X: 415, Y: 32, Z: 362
            Category: 荒らし、盗難について
            ```
            """.trimIndent(),
            embed["description"]!!.jsonPrimitive.content,
        )
        val field = embed["fields"]!!.jsonArray.single().jsonObject
        assertEquals("MESSAGE", field["name"]!!.jsonPrimitive.content)
        assertEquals("荒らし被害の報告", field["value"]!!.jsonPrimitive.content)
        assertEquals("Created by _NIKOMARU", embed["footer"]!!.jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("Omits the comment icon when the author has no UUID")
    fun omitsIconWithoutUuid() {
        val comment = TicketComment(
            id = 1,
            ticketId = ticket.id,
            authorUuid = null,
            authorName = "discord-bot",
            authorType = TicketCommentAuthorType.STAFF,
            content = "確認します",
            createdAt = Instant.EPOCH,
        )

        val payload = DiscordWebhookPayload.createComment(ticket, comment, null, TicketWebhookConfig())

        val embed = payload["embeds"]!!.jsonArray.single().jsonObject
        assertEquals("Ticket Commented - #118", embed["title"]!!.jsonPrimitive.content)
        assertEquals("Commented by discord-bot", embed["footer"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertFalse("icon_url" in embed["author"]!!.jsonObject)
    }

    @Test
    @DisplayName("Builds the close embed with the webhook sender name and icon")
    fun buildsStatusChangeEmbed() {
        val closed = ticket.copy(status = TicketStatus.CLOSED, closeReason = TicketCloseReason.DONE)
        val staff = TicketActor(null, "discord-bot", isStaff = true)
        val change = TicketStatusChange(closed, staff, Instant.parse("2026-08-18T13:05:00Z"))

        val payload = DiscordWebhookPayload.createStatusChange(change, null, TicketWebhookConfig())

        // 送信者は設定の名前とロゴになる
        assertEquals("Moripa Utils", payload["username"]!!.jsonPrimitive.content)
        assertEquals(TicketWebhookConfig.DEFAULT_ICON_URL, payload["avatar_url"]!!.jsonPrimitive.content)
        val embed = payload["embeds"]!!.jsonArray.single().jsonObject
        assertEquals("Ticket Done-marked - #118", embed["title"]!!.jsonPrimitive.content)
        // author はチケットの送信者、footer は変更した人
        assertEquals("_NIKOMARU", embed["author"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Done-marked by discord-bot", embed["footer"]!!.jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("Resolves name and UUID placeholders in the avatar URL template")
    fun resolvesAvatarTemplate() {
        assertEquals(
            "https://example.com/_NIKOMARU/$playerUuid",
            AvatarUrlTemplate.resolve("https://example.com/{name}/{uuid}", "_NIKOMARU", playerUuid),
        )
        // 空のテンプレートはアイコンを出さない
        assertNull(AvatarUrlTemplate.resolve("", "_NIKOMARU", playerUuid))
    }
}
