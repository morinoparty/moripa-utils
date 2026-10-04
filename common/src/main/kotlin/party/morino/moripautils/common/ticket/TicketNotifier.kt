/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket

import party.morino.moripautils.common.model.config.TicketCategory
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketComment

/**
 * 新しいチケットやコメントを知らせる通知先 (Observer)
 *
 * Koin に [TicketNotifier] として登録した実装すべてに、[TicketService] が送信や書き込みのたびに通知する。
 * Discord Webhook やゲーム内のスタッフ通知など、通知先を増やすときはこのインターフェースを実装して登録する。
 */
interface TicketNotifier {
    /**
     * 新しいチケットを通知する
     *
     * 通知の失敗でチケットの送信そのものが失敗しないよう、実装は例外を投げずにログへ残すこと。
     *
     * @param ticket 保存されたチケット
     * @param category チケットのカテゴリー (表示名の解決済み)
     */
    suspend fun notify(ticket: Ticket, category: TicketCategory)

    /**
     * チケットに新しいコメントが書き込まれたことを通知する
     *
     * 誰に知らせるか (本人 / 運営) は [TicketComment.authorType] を見て実装ごとに決める。
     * 通知の失敗でコメントの書き込みそのものが失敗しないよう、実装は例外を投げずにログへ残すこと。
     *
     * @param ticket コメント先のチケット
     * @param comment 保存されたコメント
     */
    suspend fun notifyComment(ticket: Ticket, comment: TicketComment)
}
