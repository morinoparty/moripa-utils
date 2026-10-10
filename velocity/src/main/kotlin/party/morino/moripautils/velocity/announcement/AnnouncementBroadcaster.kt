/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.velocity.announcement

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.velocity.MoripaUtils
import party.morino.moripautils.velocity.model.announcement.AnnouncementMessage
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * お知らせを一定の間隔で 1 件ずつ、プロキシに接続している全プレイヤーへ送る
 *
 * 最後のお知らせを送ったら先頭に戻り、繰り返し送り続ける。
 */
class AnnouncementBroadcaster : MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()
    private val server: ProxyServer by inject()

    /** 送るお知らせ ([start] で差し替える) */
    @Volatile
    private var messages: List<AnnouncementMessage> = emptyList()

    /** 次に送るお知らせの位置 ([start] で先頭に戻し、送るたびに 1 つ進める) */
    @Volatile
    private var nextIndex: Int = 0

    /** 実行中の定期タスク (停止中は null) */
    private var task: ScheduledTask? = null

    /**
     * お知らせの定期送信を始める
     *
     * 起動や再読み込みの直後は接続しているプレイヤーが少ないため、最初のお知らせも [interval] 後に送る。
     * すでに実行中の場合は、止めてから新しいお知らせで始め直す。
     *
     * @param messages 送るお知らせ (この順に送る)。空の場合は何もしない
     * @param interval お知らせを送る間隔
     */
    @Synchronized
    fun start(messages: List<AnnouncementMessage>, interval: Duration) {
        stop()
        if (messages.isEmpty()) {
            return
        }
        this.messages = messages
        nextIndex = 0
        val millis = interval.inWholeMilliseconds
        task = server.scheduler
            .buildTask(plugin, Runnable { broadcastNext() })
            .delay(millis, TimeUnit.MILLISECONDS)
            .repeat(millis, TimeUnit.MILLISECONDS)
            .schedule()
    }

    /**
     * お知らせの定期送信を止める (実行中でなければ何もしない)
     */
    @Synchronized
    fun stop() {
        task?.cancel()
        task = null
    }

    /**
     * 次のお知らせを全プレイヤーへ送り、送る位置を 1 つ進める
     */
    fun broadcastNext() {
        val current = messages
        if (current.isEmpty()) {
            return
        }
        // 再読み込みで件数が減っていても範囲外にならないよう、剰余で位置を決める
        val message = current[nextIndex % current.size]
        nextIndex = (nextIndex + 1) % current.size
        // コンソールにまで流れないよう、ProxyServer ではなくプレイヤーにだけ送る
        server.allPlayers.forEach { player -> player.sendMessage(message.content) }
    }
}
