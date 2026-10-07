/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.MoripaUtilsConfig
import party.morino.moripautils.common.model.config.TicketCategory
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketActor
import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketCommentAuthorType
import party.morino.moripautils.common.model.ticket.TicketCommentResult
import party.morino.moripautils.common.model.ticket.TicketCommentSubmission
import party.morino.moripautils.common.model.ticket.TicketSubmission
import party.morino.moripautils.common.model.ticket.TicketSubmitResult
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * チケットの送信とコメントを取りまとめるサービス (Facade)
 *
 * 入力の検証 → 保存 → 通知 の順に処理する。UI (Dialog / コマンド) や HTTP API はこのクラスだけを呼べばよい。
 * 通知先は Koin に [TicketNotifier] として登録されたものすべて (Observer) で、送信や書き込みのたびに取り出す。
 * 通知は [notificationScope] で非同期に行い、送信結果は保存が終わった時点で返す
 * (Webhook が遅くてもプレイヤーへの受付メッセージを待たせず、二重送信を誘発しないため)。
 *
 * @param logger 通知の失敗などを記録するロガー
 * @param notificationScope 通知を実行するスコープ。プラグインの無効化時に [close] で停止する
 */
class TicketService(
    private val logger: Logger,
    private val notificationScope: CoroutineScope,
) : MoripaUtilsKoinComponent {
    private val config: MoripaUtilsConfig by inject()
    private val repository: TicketRepository by inject()
    private val commentRepository: TicketCommentRepository by inject()

    /**
     * チケットを送信する
     *
     * @param playerUuid 送信したプレイヤーの UUID
     * @param playerName 送信したプレイヤーの名前
     * @param categoryId 選択されたカテゴリーの id
     * @param content 入力された本文 (前後の空白は取り除いて保存する)
     * @return 送信結果。入力に誤りがある場合は保存も通知も行わない。通知の完了は待たない
     */
    suspend fun submit(
        playerUuid: UUID,
        playerName: String,
        categoryId: String,
        content: String,
    ): TicketSubmitResult {
        val trimmedContent = content.trim()
        // Dialog の選択肢は設定から作るが、不正なクライアントや古い Dialog に備えてここでも引き当てる
        val category = config.ticket.categories.firstOrNull { it.id == categoryId }
            ?: return TicketSubmitResult.UnknownCategory(categoryId)
        // 検証に失敗した場合は副作用 (保存・通知) を起こさずに理由を返す
        validateContent(trimmedContent)?.let { return it }

        val ticket = repository.create(
            TicketSubmission(
                serverId = config.server,
                playerUuid = playerUuid,
                playerName = playerName,
                categoryId = category.id,
                content = trimmedContent,
            ),
        )
        notifyAll(ticket, category)
        return TicketSubmitResult.Success(ticket)
    }

    /**
     * チケットにコメントを書き込む
     *
     * 本人は自分のチケットに、運営はすべてのチケットに書き込める (状態が CLOSED でも書き込める)。
     *
     * @param actor 書き込む人
     * @param ticketId コメント先のチケットの id
     * @param content 入力された本文 (前後の空白は取り除いて保存する)
     * @return 書き込み結果。入力に誤りがある場合や権限がない場合は保存も通知も行わない。通知の完了は待たない
     */
    suspend fun addComment(
        actor: TicketActor,
        ticketId: Long,
        content: String,
    ): TicketCommentResult {
        val trimmedContent = content.trim()
        // 存在しないチケットと他人のチケットを区別させず、どの id が使われているかを推測させない
        val ticket = repository.findById(ticketId) ?: return TicketCommentResult.TicketNotFound
        val authorType = resolveAuthorType(ticket, actor) ?: return TicketCommentResult.TicketNotFound
        // 本文の検証はチケットの送信と同じ規則を使い、結果だけコメント用に読み替える
        when (val error = validateContent(trimmedContent)) {
            TicketSubmitResult.BlankContent -> return TicketCommentResult.BlankContent
            is TicketSubmitResult.ContentTooLong -> return TicketCommentResult.ContentTooLong(error.maxLength)
            else -> Unit
        }

        val comment = commentRepository.create(
            TicketCommentSubmission(
                ticketId = ticket.id,
                authorUuid = actor.uuid,
                authorName = actor.name,
                authorType = authorType,
                content = trimmedContent,
            ),
        )
        launchNotifications("comment #${comment.id} on ticket #${ticket.id}") { it.notifyComment(ticket, comment) }
        return TicketCommentResult.Success(ticket, comment)
    }

    /**
     * 操作する人が閲覧できるチケットを取得する
     *
     * @param actor 閲覧する人
     * @param ticketId チケットの id
     * @return チケット。存在しない場合や閲覧する権限がない場合は null
     */
    suspend fun findAccessibleTicket(actor: TicketActor, ticketId: Long): Ticket? =
        repository.findById(ticketId)?.takeIf { resolveAuthorType(it, actor) != null }

    /**
     * 操作する人が閲覧できるチケットを新しい順に取得する
     *
     * 本人は自分のチケットを、運営はすべてのチケットを閲覧できる。
     *
     * @param actor 閲覧する人
     * @param offset 先頭から読み飛ばす件数
     * @param limit 取得する最大件数
     * @return 最大 [limit] 件のチケット (新しい順)。UUID を持たない運営以外の操作者には空のリストを返す
     */
    suspend fun listAccessibleTickets(actor: TicketActor, offset: Long, limit: Int): List<Ticket> = when {
        actor.isStaff -> repository.listRecent(null, offset, limit)
        // UUID が無いと自分のチケットを特定できないため、何も返さない
        actor.uuid == null -> emptyList()
        else -> repository.listRecent(actor.uuid, offset, limit)
    }

    /**
     * チケットのコメントを古い順に取得する (閲覧権限は呼び出し側で確認済みであること)
     *
     * @param ticketId チケットの id
     * @param afterId この id より後のコメントだけを返す (先頭から取得する場合は null)
     * @param limit 取得する最大件数
     * @return コメント一覧
     */
    suspend fun listComments(ticketId: Long, afterId: Long?, limit: Int): List<TicketComment> =
        commentRepository.listByTicket(ticketId, afterId, limit)

    /**
     * チケットの最新のコメントを古い順に並べて取得する (閲覧権限は呼び出し側で確認済みであること)
     *
     * @param ticketId チケットの id
     * @param limit 取得する最大件数
     * @return 最新 [limit] 件のコメント
     */
    suspend fun listRecentComments(ticketId: Long, limit: Int): List<TicketComment> =
        commentRepository.listRecent(ticketId, limit)

    /**
     * 登録されているすべての通知先へ新しいチケットの通知を開始する (完了は待たない)
     *
     * @param ticket 保存されたチケット
     * @param category チケットのカテゴリー
     */
    private fun notifyAll(ticket: Ticket, category: TicketCategory) {
        launchNotifications("ticket #${ticket.id}") { it.notify(ticket, category) }
    }

    /**
     * 登録されているすべての通知先に対して通知処理を開始する (完了は待たない)
     *
     * 1 つの通知先が失敗しても他の通知先や保存結果に影響しないよう、通知先ごとに例外を捕捉する。
     *
     * @param target ログに残す通知対象の説明
     * @param send 通知先ごとに実行する通知処理
     */
    private fun launchNotifications(target: String, send: suspend (TicketNotifier) -> Unit) {
        // Webhook の応答待ちでゲーム内通知が遅れないよう、通知先ごとに別のコルーチンで並行して送る
        getKoin().getAll<TicketNotifier>().forEach { notifier ->
            notificationScope.launch {
                try {
                    send(notifier)
                } catch (e: CancellationException) {
                    // コルーチンのキャンセルは握りつぶさずに伝える
                    throw e
                } catch (e: Exception) {
                    // 通知先の実装は例外を投げない約束だが、念のためここでも保存結果を守る
                    val notifierName = notifier::class.simpleName
                    logger.log(Level.WARNING, "Ticket notifier $notifierName failed for $target", e)
                }
            }
        }
    }

    /**
     * 実行中の通知を取り消し、以降の通知を行わないようにする (プラグインの無効化時に呼ぶ)
     */
    fun close() {
        notificationScope.cancel()
    }

    companion object {
        /** 本文の最大文字数 (Dialog の入力欄の上限にも使う) */
        const val MAX_CONTENT_LENGTH: Int = 1000

        /**
         * 本文を検証する (外部状態に依存しない純粋関数)
         *
         * @param content 前後の空白を取り除いた本文
         * @return 不正な場合はその理由、問題なければ null
         */
        fun validateContent(content: String): TicketSubmitResult? = when {
            content.isBlank() -> TicketSubmitResult.BlankContent
            // Discord の Embed や DB 容量を考慮して上限を設ける
            content.length > MAX_CONTENT_LENGTH -> TicketSubmitResult.ContentTooLong(MAX_CONTENT_LENGTH)
            else -> null
        }

        /**
         * チケットに対する操作する人の立場を判定する (外部状態に依存しない純粋関数)
         *
         * 運営が自分で送ったチケットに書き込む場合は、送信者本人として扱う。
         *
         * @param ticket 対象のチケット
         * @param actor 操作する人
         * @return 本人なら PLAYER、運営なら STAFF、どちらでもなければ null (操作できない)
         */
        fun resolveAuthorType(ticket: Ticket, actor: TicketActor): TicketCommentAuthorType? = when {
            actor.uuid != null && actor.uuid == ticket.playerUuid -> TicketCommentAuthorType.PLAYER
            actor.isStaff -> TicketCommentAuthorType.STAFF
            else -> null
        }
    }
}
