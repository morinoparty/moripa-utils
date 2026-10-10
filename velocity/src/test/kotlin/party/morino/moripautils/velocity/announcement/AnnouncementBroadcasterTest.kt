/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.velocity.announcement

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.config.AnnouncementConfig
import party.morino.moripautils.velocity.MoripaUtils
import party.morino.moripautils.velocity.model.announcement.AnnouncementMessage

class AnnouncementBroadcasterTest {
    private val player = mockk<Player>(relaxed = true)
    private val guest = mockk<Player>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // スケジューラーは使わずに broadcastNext を直接呼ぶため、タスクの登録はモックで受け流す
        val server = mockk<ProxyServer>(relaxed = true)
        every { server.allPlayers } returns listOf(player, guest)
        every { player.hasPermission(PERMISSION) } returns true
        every { guest.hasPermission(PERMISSION) } returns false
        MoripaUtilsKoinContext.start(
            listOf(
                module {
                    single<ProxyServer> { server }
                    single<MoripaUtils> { mockk() }
                },
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        MoripaUtilsKoinContext.stop()
    }

    @Test
    @DisplayName("Broadcasts messages in order and wraps around")
    fun broadcastsInOrder() {
        val first = Component.text("first")
        val second = Component.text("second")
        val broadcaster = AnnouncementBroadcaster()
        broadcaster.start(listOf(AnnouncementMessage("a", first), AnnouncementMessage("b", second)), AnnouncementConfig())

        repeat(3) { broadcaster.broadcastNext() }

        // 最後まで送ったら先頭に戻る
        verifyOrder {
            player.sendMessage(first)
            player.sendMessage(second)
            player.sendMessage(first)
        }
    }

    @Test
    @DisplayName("Sends only to players with the configured permission")
    fun sendsOnlyToPermittedPlayers() {
        val message = Component.text("vote")
        val broadcaster = AnnouncementBroadcaster()
        broadcaster.start(listOf(AnnouncementMessage("vote", message)), AnnouncementConfig(permission = PERMISSION))

        broadcaster.broadcastNext()

        verify(exactly = 1) { player.sendMessage(message) }
        verify(exactly = 0) { guest.sendMessage(message) }
    }

    private companion object {
        /** お知らせを受け取る権限 */
        const val PERMISSION: String = "moripautils.announcement.receive"
    }
}
