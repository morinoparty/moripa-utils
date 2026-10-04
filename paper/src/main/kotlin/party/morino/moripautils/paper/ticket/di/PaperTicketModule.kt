/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket.di

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import party.morino.moripautils.common.ticket.TicketNotifier
import party.morino.moripautils.paper.ticket.InGameTicketNotifier
import party.morino.moripautils.paper.ticket.dialog.TicketCommentDialogFactory
import party.morino.moripautils.paper.ticket.dialog.TicketCommentDialogSubmitHandler
import party.morino.moripautils.paper.ticket.dialog.TicketDialogFactory
import party.morino.moripautils.paper.ticket.dialog.TicketDialogSubmitHandler
import party.morino.moripautils.paper.ticket.view.TicketThreadPresenter

/**
 * ticket 機能のうち Paper 固有の部分 (Dialog / スレッド表示 / ゲーム内通知) の Koin モジュールを生成するファクトリ
 *
 * 共通部分 ([party.morino.moripautils.common.ticket.di.TicketModule]) と一緒に読み込む。
 */
object PaperTicketModule {
    /**
     * Paper 向けの ticket モジュールを生成する
     *
     * @return Dialog の生成 / 送信処理 / スレッド表示 / ゲーム内通知をシングルトンとして提供する Koin モジュール
     */
    fun create(): Module = module {
        single { TicketDialogFactory() }
        single { TicketDialogSubmitHandler() }
        single { TicketCommentDialogFactory() }
        single { TicketCommentDialogSubmitHandler() }
        single { TicketThreadPresenter() }
        // TicketService が getAll<TicketNotifier>() で Webhook 通知と一緒に取り出す
        single { InGameTicketNotifier() } bind TicketNotifier::class
    }
}
