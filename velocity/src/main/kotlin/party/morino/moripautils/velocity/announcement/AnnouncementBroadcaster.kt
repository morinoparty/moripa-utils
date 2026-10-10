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
import com.velocitypowered.api.scheduler.ScheduledTask
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.AnnouncementConfig
import party.morino.moripautils.velocity.MoripaUtils
import party.morino.moripautils.velocity.model.announcement.AnnouncementMessage
import java.util.concurrent.TimeUnit

/**
 * お知らせを一定の間隔で 1 件ずつ、プロキシに接続しているプレイヤーへ送る
 *
 * すべてのお知らせを送ったら最初に戻り、繰り返し送り続ける。
 */
class AnnouncementBroadcaster : MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()
    private val server: ProxyServer by inject()

    /** 送るお知らせ ([start] で差し替える) */
    @Volatile
    private var messages: List<AnnouncementMessage> = emptyList()

    /** お知らせ機能の設定 ([start] で差し替える) */
    @Volatile
    private var config: AnnouncementConfig = AnnouncementConfig()

    /** 今の周回で送る順番 (ランダムな順番の場合は周回ごとに並べ直す) */
    private var order: List<AnnouncementMessage> = emptyList()

    /** 今の周回で次に送るお知らせの位置 */
    private var nextIndex: Int = 0

    /** 実行中の定期タスク (停止中は null) */
    private var task: ScheduledTask? = null

    /**
     * お知らせの定期送信を始める
     *
     * 最初のお知らせは startupDelay 後に送り、以降は interval ごとに送る。
     * すでに実行中の場合は、止めてから新しいお知らせで始め直す。
     *
     * @param messages 送るお知らせ (ランダムな順番にしない場合はこの順に送る)。空の場合は何もしない
     * @param config お知らせ機能の設定
     */
    @Synchronized
    fun start(messages: List<AnnouncementMessage>, config: AnnouncementConfig) {
        stop()
        if (messages.isEmpty()) {
            return
        }
        this.messages = messages
        this.config = config
        order = emptyList()
        nextIndex = 0
        task = server.scheduler
            .buildTask(plugin, Runnable { broadcastNext() })
            .delay(config.startupDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .repeat(config.interval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
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
     * 次のお知らせを受け取るプレイヤーへ送り、送る位置を 1 つ進める
     */
    @Synchronized
    fun broadcastNext() {
        if (messages.isEmpty()) {
            return
        }
        // 周回の始めに送る順番を決める (ランダムな場合も 1 周の中では同じお知らせを繰り返さない)
        if (nextIndex >= order.size) {
            order = if (config.randomOrder) messages.shuffled() else messages
            nextIndex = 0
        }
        val message = order[nextIndex]
        nextIndex++
        recipients().forEach { player -> player.sendMessage(message.content) }
    }

    /**
     * お知らせを受け取るプレイヤーを返す
     *
     * @return permission が空の場合は全プレイヤー、そうでなければ permission を持つプレイヤー
     */
    private fun recipients(): List<Player> {
        val permission = config.permission
        // コンソールにまで流れないよう、ProxyServer ではなくプレイヤーにだけ送る
        return server.allPlayers.filter { player -> permission.isEmpty() || player.hasPermission(permission) }
    }
}
