/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import party.morino.moripautils.common.ticket.TicketCommentRepository
import party.morino.moripautils.common.ticket.TicketNotifier
import party.morino.moripautils.common.ticket.TicketRepository
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.common.ticket.database.ExposedTicketCommentRepository
import party.morino.moripautils.common.ticket.database.ExposedTicketRepository
import party.morino.moripautils.common.ticket.database.TicketDatabaseSchema
import party.morino.moripautils.common.ticket.webhook.TicketWebhookNotifier
import java.util.logging.Logger

/**
 * ticket 機能の Koin モジュールを生成するファクトリ
 *
 * Exposed に依存するクラスを含むため、ticket 機能が有効な Paper でだけ読み込むこと
 * (Velocity の JAR には Exposed を同梱していない)。
 * チケットは共有データベースに保存するため、[party.morino.moripautils.common.database.di.DatabaseModule] と一緒に読み込む。
 */
object TicketModule {
    /**
     * ticket 機能の共通部分 (リポジトリ / Webhook 通知 / サービス) のモジュールを生成する
     *
     * @param logger 通知の失敗などを記録するロガー
     * @return リポジトリ / Webhook 通知 / サービスをシングルトンとして提供する Koin モジュール
     */
    fun create(logger: Logger): Module = module {
        // 共有データベースの tickets テーブルに保存する (接続先は config.conf の database で決まる)
        // tickets / ticket_comments テーブルの作成は両リポジトリで共有する
        single { TicketDatabaseSchema() }
        single<TicketRepository> { ExposedTicketRepository() }
        single<TicketCommentRepository> { ExposedTicketCommentRepository() }
        // 通知先は TicketNotifier として bind し、TicketService が getAll でまとめて取り出す
        single { TicketWebhookNotifier(logger) } bind TicketNotifier::class
        // 通知は送信結果の返却と切り離して行う。SupervisorJob により 1 件の失敗が他の通知を巻き込まない
        single { TicketService(logger, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    }
}
