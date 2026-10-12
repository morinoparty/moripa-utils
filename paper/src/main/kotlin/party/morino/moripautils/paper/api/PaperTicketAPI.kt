/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.api

import com.github.shynixn.mccoroutine.bukkit.scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import party.morino.moripautils.api.model.ticket.Ticket
import party.morino.moripautils.api.model.ticket.TicketActor
import party.morino.moripautils.api.model.ticket.TicketCloseReason
import party.morino.moripautils.api.model.ticket.TicketComment
import party.morino.moripautils.api.model.ticket.TicketCommentResult
import party.morino.moripautils.api.model.ticket.TicketListFilter
import party.morino.moripautils.api.model.ticket.TicketStatusChangeResult
import party.morino.moripautils.api.ticket.TicketAPI
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.paper.MoripaUtils
import java.util.concurrent.CompletableFuture

/**
 * [TicketAPI] の Paper 向けの実装 (Adapter)
 *
 * 権限の判定や通知は [TicketService] にそのまま任せ、ここでは Future への変換と引数の検証だけを行う。
 * 再読み込みのたびに Koin コンテナが作り直されるため、サービスは呼び出しのたびにコンテナから取り出す。
 */
class PaperTicketAPI : TicketAPI {
    override fun getTicket(actor: TicketActor, ticketId: Long): CompletableFuture<Ticket?> =
        runWithService { service -> service.findAccessibleTicket(actor, ticketId) }

    override fun listTickets(
        actor: TicketActor,
        filter: TicketListFilter,
        offset: Long,
        limit: Int,
    ): CompletableFuture<List<Ticket>> {
        // 呼び出し側のバグは早めに気付けるよう、サービスに渡す前に失敗させる
        if (offset < 0) {
            return CompletableFuture.failedFuture(IllegalArgumentException("offset must not be negative, but was $offset"))
        }
        validateLimit(limit)?.let { return CompletableFuture.failedFuture(it) }
        return runWithService { service ->
            service.listAccessibleTickets(actor, filter, offset, limit.coerceAtMost(TicketAPI.MAX_LIMIT))
        }
    }

    override fun closeTicket(
        actor: TicketActor,
        ticketId: Long,
        reason: TicketCloseReason,
    ): CompletableFuture<TicketStatusChangeResult> = runWithService { service -> service.closeTicket(actor, ticketId, reason) }

    override fun reopenTicket(actor: TicketActor, ticketId: Long): CompletableFuture<TicketStatusChangeResult> =
        runWithService { service -> service.reopenTicket(actor, ticketId) }

    override fun addComment(actor: TicketActor, ticketId: Long, content: String): CompletableFuture<TicketCommentResult> =
        runWithService { service -> service.addComment(actor, ticketId, content) }

    override fun listComments(
        actor: TicketActor,
        ticketId: Long,
        afterId: Long?,
        limit: Int,
    ): CompletableFuture<List<TicketComment>?> {
        validateLimit(limit)?.let { return CompletableFuture.failedFuture(it) }
        return runWithService { service ->
            // TicketService.listComments は権限を確認しないため、閲覧できないチケットのコメントを読ませないようここで確認する
            service.findAccessibleTicket(actor, ticketId)?.let {
                service.listComments(ticketId, afterId, limit.coerceAtMost(TicketAPI.MAX_LIMIT))
            }
        }
    }

    /**
     * 取得件数を検証する (外部状態に依存しない)
     *
     * @param limit 取得する最大件数
     * @return 不正な場合は例外、問題なければ null
     */
    private fun validateLimit(limit: Int): IllegalArgumentException? =
        if (limit < 1) IllegalArgumentException("limit must be at least 1, but was $limit") else null

    /**
     * プラグインのコルーチンスコープでチケットの処理を行い、結果を Future に変換する
     *
     * プラグインが無効化されるとスコープごとキャンセルされ、Future もキャンセル扱いで完了する。
     * データベースへのアクセスでメインスレッドを止めないよう I/O スレッドで実行する。
     *
     * @param block サービスを受け取って結果を返す処理
     * @return 処理結果で完了する Future。ticket 機能が無効な場合は [IllegalStateException] で完了する
     */
    private fun <T> runWithService(block: suspend (TicketService) -> T): CompletableFuture<T> {
        // プラグインが有効になっていない場合はコンテナが存在しない
        val koin = MoripaUtilsKoinContext.getOrNull()
            ?: return CompletableFuture.failedFuture(IllegalStateException("MoripaUtils is not enabled"))
        // TicketService は ticket 機能が有効な場合だけ読み込まれる
        val service = koin.getOrNull<TicketService>()
            ?: return CompletableFuture.failedFuture(IllegalStateException("Ticket feature is disabled"))
        val plugin = koin.get<MoripaUtils>()
        return plugin.scope.future(Dispatchers.IO) { block(service) }
    }
}
