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
import io.mockk.verifyOrder
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.velocity.MoripaUtils
import party.morino.moripautils.velocity.model.announcement.AnnouncementMessage
import kotlin.time.Duration.Companion.minutes

class AnnouncementBroadcasterTest {
    private val player = mockk<Player>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // スケジューラーは使わずに broadcastNext を直接呼ぶため、タスクの登録はモックで受け流す
        val server = mockk<ProxyServer>(relaxed = true)
        every { server.allPlayers } returns listOf(player)
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
        broadcaster.start(listOf(AnnouncementMessage("a", first), AnnouncementMessage("b", second)), 30.minutes)

        repeat(3) { broadcaster.broadcastNext() }

        // 最後まで送ったら先頭に戻る
        verifyOrder {
            player.sendMessage(first)
            player.sendMessage(second)
            player.sendMessage(first)
        }
    }
}
