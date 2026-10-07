/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket.webhook

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.TicketCategory
import party.morino.moripautils.common.model.config.TicketConfig
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketCommentAuthorType
import party.morino.moripautils.common.model.ticket.TicketStatusChange
import party.morino.moripautils.common.ticket.TicketNotifier
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 新しいチケット、プレイヤーからのコメント、状態の変更 (クローズ / 再オープン) を Discord Webhook へ送る通知先
 *
 * config.conf の ticket.webhook.url が空の場合は何もしない。
 * 運営のコメントは運営自身が書いたものなので送らない。
 * 送信に失敗してもチケットの送信自体は成功させたいため、例外は呼び出し元へ投げずにログへ残す。
 *
 * @param logger 送信の失敗を記録するロガー
 */
class TicketWebhookNotifier(
    private val logger: Logger,
) : TicketNotifier,
    MoripaUtilsKoinComponent {
    private val config: TicketConfig by inject()

    /** Webhook 送信用の HTTP クライアント (URL が設定されているときだけ生成する) */
    private val httpClient: HttpClient by lazy {
        HttpClient
            .newBuilder()
            .connectTimeout(TIMEOUT)
            .build()
    }

    override suspend fun notify(ticket: Ticket, category: TicketCategory) {
        send(DiscordWebhookPayload.create(ticket, category, config.webhook), "ticket #${ticket.id}")
    }

    override suspend fun notifyComment(ticket: Ticket, comment: TicketComment) {
        // 運営の返信は運営チャンネルに流す必要がないため、プレイヤーからのコメントだけを送る
        if (comment.authorType != TicketCommentAuthorType.PLAYER) {
            return
        }
        send(
            DiscordWebhookPayload.createComment(ticket, comment, categoryNameOf(ticket), config.webhook),
            "comment #${comment.id} on ticket #${ticket.id}",
        )
    }

    override suspend fun notifyStatusChange(change: TicketStatusChange) {
        // 運営チャンネルで対応状況を追えるよう、本人と運営のどちらが変更しても送る
        send(
            DiscordWebhookPayload.createStatusChange(change, categoryNameOf(change.ticket), config.webhook),
            "status change of ticket #${change.ticket.id}",
        )
    }

    /**
     * チケットのカテゴリーの表示名を引き当てる
     *
     * @param ticket 対象のチケット
     * @return 表示名。カテゴリーが設定から削除されている場合は null (呼び出し側で id をそのまま表示する)
     */
    private fun categoryNameOf(ticket: Ticket): String? = config.categories.firstOrNull { it.id == ticket.categoryId }?.name

    /**
     * Webhook に本文を送る
     *
     * @param payload 送る JSON
     * @param target ログに残す通知対象の説明
     */
    private suspend fun send(payload: JsonObject, target: String) {
        val url = config.webhook.url
        // URL 未設定は「Webhook を使わない」という意味なので正常系として扱う
        if (url.isBlank()) {
            return
        }
        try {
            val request = HttpRequest
                .newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build()
            // sendAsync + await でスレッドをブロックせずに応答を待つ
            val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
            if (response.statusCode() !in SUCCESS_STATUS) {
                // 応答本文はエラー内容の手掛かりになるが、長すぎるとログが読みにくいので先頭だけ残す
                val body = response.body().take(MAX_LOGGED_BODY_LENGTH)
                logger.warning("Ticket webhook returned HTTP ${response.statusCode()} for $target: $body")
            }
        } catch (e: CancellationException) {
            // コルーチンのキャンセルは握りつぶさずに伝える
            throw e
        } catch (e: Exception) {
            // URL の書式誤り / 接続失敗 / タイムアウトなどはすべてログに残すだけにする
            logger.log(Level.WARNING, "Failed to send ticket webhook for $target", e)
        }
    }

    companion object {
        /** 接続と応答待ちのタイムアウト */
        private val TIMEOUT: Duration = Duration.ofSeconds(10)

        /** 成功とみなす HTTP ステータス (Discord は 204 No Content を返す) */
        private val SUCCESS_STATUS = 200..299

        /** 失敗時にログへ残す応答本文の最大文字数 */
        private const val MAX_LOGGED_BODY_LENGTH = 200
    }
}
