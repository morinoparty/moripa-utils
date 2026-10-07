/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.ticket.webhook

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import party.morino.moripautils.common.model.config.TicketCategory
import party.morino.moripautils.common.model.config.TicketWebhookConfig
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketCloseReason
import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketStatus
import party.morino.moripautils.common.model.ticket.TicketStatusChange

/**
 * 新しいチケットやコメント、状態の変更を知らせる Discord Webhook の本文 (JSON) を組み立てる
 *
 * Embed は次の形にそろえる。
 * - author: チケットを送信したプレイヤー (コメントは書き込んだ人) の名前とアイコン
 * - title: 「Ticket Created - #118」のような操作内容とチケット番号
 * - description: チケットの情報 (UUID / サーバー / ワールド / 座標 / カテゴリー) をコードブロックで並べる
 * - field: MESSAGE として本文
 * - footer: 「Created by Falp06」のような操作者とアイコン、timestamp
 *
 * 外部状態に依存しない純粋関数だけを持つ。
 */
object DiscordWebhookPayload {
    /** Embed の field の値の最大文字数 (Discord の制限) */
    private const val MAX_FIELD_VALUE_LENGTH = 1024

    /** 新しいチケットの Embed の色 (緑系) */
    private const val EMBED_COLOR = 0x4CAF50

    /** コメントの Embed の色 (青系、新しいチケットと見分けやすくする) */
    private const val COMMENT_EMBED_COLOR = 0x2196F3

    /** クローズの Embed の色 (赤系) */
    private const val CLOSED_EMBED_COLOR = 0xF44336

    /** 再オープンの Embed の色 (橙系) */
    private const val REOPENED_EMBED_COLOR = 0xFF9800

    /** 本文を表示する field の名前 */
    private const val MESSAGE_FIELD_NAME = "MESSAGE"

    /**
     * チケット 1 件分の Webhook 本文を組み立てる
     *
     * @param ticket 通知するチケット
     * @param category チケットのカテゴリー
     * @param config Webhook の設定 (送信者名とアイコン画像 URL のテンプレートを使う)
     * @return Discord の Execute Webhook API に送る JSON
     */
    fun create(ticket: Ticket, category: TicketCategory, config: TicketWebhookConfig): JsonObject {
        val avatarUrl = AvatarUrlTemplate.resolve(config.avatarUrl, ticket.playerName, ticket.playerUuid)
        return payload(
            config,
            EmbedContent(
                title = "Ticket Created - #${ticket.id}",
                authorName = ticket.playerName,
                authorIcon = avatarUrl,
                info = ticketInfo(ticket, category.name),
                message = ticket.content,
                footerText = "Created by ${ticket.playerName}",
                footerIcon = avatarUrl,
                color = EMBED_COLOR,
                timestamp = ticket.createdAt.toString(),
            ),
        )
    }

    /**
     * チケットへのコメント 1 件分の Webhook 本文を組み立てる
     *
     * @param ticket コメント先のチケット
     * @param comment 通知するコメント
     * @param categoryName チケットのカテゴリーの表示名 (設定から削除されている場合は null で、id をそのまま表示する)
     * @param config Webhook の設定 (送信者名とアイコン画像 URL のテンプレートを使う)
     * @return Discord の Execute Webhook API に送る JSON
     */
    fun createComment(
        ticket: Ticket,
        comment: TicketComment,
        categoryName: String?,
        config: TicketWebhookConfig,
    ): JsonObject {
        // アイコンはチケットの送信者ではなく、コメントを書き込んだ人のものにする
        val avatarUrl = AvatarUrlTemplate.resolve(config.avatarUrl, comment.authorName, comment.authorUuid)
        return payload(
            config,
            EmbedContent(
                title = "Ticket Commented - #${ticket.id}",
                authorName = comment.authorName,
                authorIcon = avatarUrl,
                info = ticketInfo(ticket, categoryName ?: ticket.categoryId),
                message = comment.content,
                footerText = "Commented by ${comment.authorName}",
                footerIcon = avatarUrl,
                color = COMMENT_EMBED_COLOR,
                timestamp = comment.createdAt.toString(),
            ),
        )
    }

    /**
     * チケットのクローズ / 再オープン 1 件分の Webhook 本文を組み立てる
     *
     * author はチケットを送信したプレイヤー、footer は状態を変更した人にする。
     *
     * @param change 状態の変更
     * @param categoryName チケットのカテゴリーの表示名 (設定から削除されている場合は null で、id をそのまま表示する)
     * @param config Webhook の設定 (送信者名とアイコン画像 URL のテンプレートを使う)
     * @return Discord の Execute Webhook API に送る JSON
     */
    fun createStatusChange(change: TicketStatusChange, categoryName: String?, config: TicketWebhookConfig): JsonObject {
        val ticket = change.ticket
        val actor = change.actor
        val (action, color) = statusChangeAction(ticket)
        return payload(
            config,
            EmbedContent(
                title = "Ticket $action - #${ticket.id}",
                authorName = ticket.playerName,
                authorIcon = AvatarUrlTemplate.resolve(config.avatarUrl, ticket.playerName, ticket.playerUuid),
                info = ticketInfo(ticket, categoryName ?: ticket.categoryId),
                message = ticket.content,
                footerText = "$action by ${actor.name}",
                footerIcon = AvatarUrlTemplate.resolve(config.avatarUrl, actor.name, actor.uuid),
                color = color,
                timestamp = change.changedAt.toString(),
            ),
        )
    }

    /**
     * Embed の description に載せるチケットの情報 (コードブロック) を組み立てる
     *
     * YAML として色付けされるよう「キー: 値」の形で並べる。
     *
     * @param ticket 対象のチケット
     * @param categoryName 表示するカテゴリー名
     * @return コードブロックで囲んだチケットの情報
     */
    fun ticketInfo(ticket: Ticket, categoryName: String): String {
        val location = ticket.location
        val lines = listOf(
            "UUID: ${ticket.playerUuid}",
            "Server: ${ticket.serverId}",
            "World: ${location.world}",
            "Location: X: ${location.x}, Y: ${location.y}, Z: ${location.z}",
            "Category: ${categoryName}",
        )
        // 値にバッククォートが含まれるとコードブロックが途中で閉じてしまうため取り除く
        val body = lines.joinToString("\n") { it.replace("`", "") }
        return "```yaml\n$body\n```"
    }

    /**
     * 状態の変更をタイトルとフッターに使う動詞と Embed の色に変換する
     *
     * @param ticket 変更後のチケット
     * @return 動詞 (例: Done-marked) と Embed の色
     */
    private fun statusChangeAction(ticket: Ticket): Pair<String, Int> = when (ticket.status) {
        TicketStatus.OPEN -> "Reopened" to REOPENED_EMBED_COLOR
        TicketStatus.CLOSED -> when (ticket.closeReason ?: TicketCloseReason.DEFAULT) {
            TicketCloseReason.DONE -> "Done-marked"
            TicketCloseReason.NOT_PLANNED -> "Closed as Not Planned"
            TicketCloseReason.DUPLICATE -> "Closed as Duplicate"
        } to CLOSED_EMBED_COLOR
    }

    /**
     * Embed を 1 件含む Webhook 本文を組み立てる
     *
     * @param config Webhook の設定 (送信者名と送信者アイコンを使う)
     * @param content Embed に表示する内容
     * @return Discord の Execute Webhook API に送る JSON
     */
    private fun payload(config: TicketWebhookConfig, content: EmbedContent): JsonObject = buildJsonObject {
        // 空の場合は Discord 側で設定した Webhook の名前やアイコンをそのまま使う
        if (config.username.isNotBlank()) {
            put("username", config.username)
        }
        if (config.iconUrl.isNotBlank()) {
            put("avatar_url", config.iconUrl)
        }
        putNoMentions()
        putJsonArray("embeds") {
            addJsonObject { putEmbed(content) }
        }
    }

    /**
     * Embed 1 件分の項目を書き込む
     *
     * @param content Embed に表示する内容
     */
    private fun JsonObjectBuilder.putEmbed(content: EmbedContent) {
        putJsonObject("author") {
            put("name", content.authorName)
            content.authorIcon?.let { put("icon_url", it) }
        }
        put("title", content.title)
        put("description", content.info)
        put("color", content.color)
        putJsonArray("fields") {
            addJsonObject {
                put("name", MESSAGE_FIELD_NAME)
                put("value", content.message.take(MAX_FIELD_VALUE_LENGTH))
                put("inline", false)
            }
        }
        putJsonObject("footer") {
            put("text", content.footerText)
            content.footerIcon?.let { put("icon_url", it) }
        }
        put("timestamp", content.timestamp)
    }

    /**
     * 本文に @everyone などが含まれていてもメンションが飛ばないようにする設定を追加する
     */
    private fun JsonObjectBuilder.putNoMentions() {
        putJsonObject("allowed_mentions") {
            putJsonArray("parse") {}
        }
    }
}
