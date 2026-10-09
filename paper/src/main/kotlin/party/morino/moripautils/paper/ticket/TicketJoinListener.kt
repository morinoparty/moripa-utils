/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.paper.MoripaUtils
import java.util.logging.Level
import kotlin.time.Duration.Companion.seconds

/**
 * 運営 (moripautils.ticket.staff) がサーバーに参加したときに、未対応のチケットの件数を知らせるリスナー
 *
 * 未対応のチケットが無い場合は何も表示しない。
 */
class TicketJoinListener :
    Listener,
    MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()
    private val service: TicketService by inject()

    /**
     * 運営の参加時に未対応のチケットの件数を表示する
     *
     * 件数の取得はデータベースへの I/O を伴うため、コルーチンでメインスレッドを止めずに行う。
     *
     * @param event 参加イベント
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        // 権限はイベント中 (メインスレッド) に確認し、運営以外では DB に問い合わせない
        if (!player.hasPermission(TicketPermissions.STAFF)) {
            return
        }
        plugin.launch {
            // 参加メッセージや他プラグインの案内に埋もれないよう、少し待ってから表示する
            delay(JOIN_MESSAGE_DELAY)
            try {
                val openCount = service.countOpenTickets()
                // 問い合わせ中に退出した場合や、未対応が無い場合は表示しない
                if (openCount == 0L || !player.isOnline) {
                    return@launch
                }
                player.sendRichMessage(
                    "<gold>[Ticket]</gold> 未対応のお問い合わせが <yellow><count></yellow> 件あります " +
                        "<click:run_command:'/ticket list --status open'><aqua>[一覧]</aqua></click>",
                    Placeholder.unparsed("count", openCount.toString()),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 参加時の案内に失敗しても、プレイヤーの操作には影響させずログに残すだけにする
                plugin.logger.log(Level.WARNING, "Failed to count open tickets for ${player.name}", e)
            }
        }
    }

    companion object {
        /** 参加してから表示するまでの待ち時間 */
        private val JOIN_MESSAGE_DELAY = 1.seconds
    }
}
