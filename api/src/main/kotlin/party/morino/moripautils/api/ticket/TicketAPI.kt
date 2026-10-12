/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.api.ticket

import party.morino.moripautils.api.model.ticket.Ticket
import party.morino.moripautils.api.model.ticket.TicketActor
import party.morino.moripautils.api.model.ticket.TicketCloseReason
import party.morino.moripautils.api.model.ticket.TicketComment
import party.morino.moripautils.api.model.ticket.TicketCommentResult
import party.morino.moripautils.api.model.ticket.TicketListFilter
import party.morino.moripautils.api.model.ticket.TicketStatusChangeResult
import java.util.concurrent.CompletableFuture

/**
 * チケット (/ticket によるお問い合わせ) を閲覧・操作する API
 *
 * どの操作も呼び出し側が指定した [TicketActor] として行い、/ticket コマンドや HTTP API と同じ権限で判定する。
 * 本人 ([TicketActor.uuid] がチケットの送信者と一致) は自分のチケットを、運営 ([TicketActor.isStaff]) はすべてのチケットを扱える。
 * 閲覧できないチケットは存在しないものとして扱う (どちらかを区別させない)。
 *
 * Java のプラグインからも使えるよう、結果は [CompletableFuture] で返す。
 * 入力の誤りや権限不足は例外ではなく結果の値 ([TicketStatusChangeResult] など) で返す。
 * ticket 機能が無効な場合や MoripaUtils が有効化されていない場合は [IllegalStateException] で、
 * limit が 1 未満などの不正な引数は [IllegalArgumentException] で例外的に完了する。
 * Future はメインスレッド以外で完了することがあるため、Bukkit API を使う後続処理はメインスレッドへ戻してから行うこと。
 */
interface TicketAPI {
    /**
     * チケットを取得する
     *
     * @param actor 閲覧する人
     * @param ticketId チケットの id
     * @return チケット。存在しない場合や閲覧する権限がない場合は null
     */
    fun getTicket(actor: TicketActor, ticketId: Long): CompletableFuture<Ticket?>

    /**
     * 閲覧できるチケットを新しい順に取得する
     *
     * 運営以外はプレイヤー名による絞り込みを指定しても無視し、常に自分のチケットだけを返す。
     *
     * @param actor 閲覧する人
     * @param filter 状態やプレイヤー名による絞り込み条件
     * @param offset 先頭から読み飛ばす件数 (0 以上)
     * @param limit 取得する最大件数 (1 以上。[MAX_LIMIT] を超える値は切り詰める)
     * @return チケットの一覧 (新しい順)
     */
    fun listTickets(actor: TicketActor, filter: TicketListFilter, offset: Long, limit: Int): CompletableFuture<List<Ticket>>

    /**
     * チケットをクローズする
     *
     * 本人は自分のチケットを、運営はすべてのチケットをクローズできる。成功すると Discord やゲーム内に通知する。
     * [comment] を指定すると、クローズする前にコメントとして書き込み、Discord の通知にもそのコメントを載せる。
     *
     * @param actor クローズする人
     * @param ticketId チケットの id
     * @param reason クローズする理由
     * @param comment クローズと同時に書き込むコメント (書き込まない場合は null。前後の空白は取り除いて保存する)
     * @return 変更結果 (既にクローズされている場合は [TicketStatusChangeResult.AlreadyClosed]、
     *   コメントが不正な場合は [TicketStatusChangeResult.BlankComment] / [TicketStatusChangeResult.CommentTooLong])
     */
    fun closeTicket(
        actor: TicketActor,
        ticketId: Long,
        reason: TicketCloseReason,
        comment: String?,
    ): CompletableFuture<TicketStatusChangeResult>

    /**
     * クローズされたチケットをオープンに戻す
     *
     * 運営だけが行える (本人の場合は [TicketStatusChangeResult.NotPermitted])。成功すると Discord やゲーム内に通知する。
     *
     * @param actor 再オープンする人
     * @param ticketId チケットの id
     * @return 変更結果 (既にオープンしている場合は [TicketStatusChangeResult.AlreadyOpen])
     */
    fun reopenTicket(actor: TicketActor, ticketId: Long): CompletableFuture<TicketStatusChangeResult>

    /**
     * チケットにコメントを書き込む
     *
     * 本人は自分のチケットに、運営はすべてのチケットに書き込める (クローズ済みでも書き込める)。成功すると Discord やゲーム内に通知する。
     *
     * @param actor 書き込む人 (名前はコメントの書き込み者名として保存する)
     * @param ticketId コメント先のチケットの id
     * @param content 本文 (前後の空白は取り除いて保存する)
     * @return 書き込み結果
     */
    fun addComment(actor: TicketActor, ticketId: Long, content: String): CompletableFuture<TicketCommentResult>

    /**
     * チケットのコメントを古い順に取得する
     *
     * @param actor 閲覧する人
     * @param ticketId チケットの id
     * @param afterId この id より後のコメントだけを返す (先頭から取得する場合は null)
     * @param limit 取得する最大件数 (1 以上。[MAX_LIMIT] を超える値は切り詰める)
     * @return コメントの一覧。チケットが存在しない場合や閲覧する権限がない場合は null
     */
    fun listComments(actor: TicketActor, ticketId: Long, afterId: Long?, limit: Int): CompletableFuture<List<TicketComment>?>

    companion object {
        /** 一覧の取得で 1 回に返す最大件数 */
        const val MAX_LIMIT: Int = 100
    }
}
