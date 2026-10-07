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
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketComment
import java.time.Instant

/**
 * 新しいチケットやコメントを知らせる Discord Webhook の本文 (JSON) を組み立てる
 *
 * Embed は次の形にそろえる。
 * - author: 操作したプレイヤーの名前とアイコン
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

    /** 本文を表示する field の名前 */
    private const val MESSAGE_FIELD_NAME = "MESSAGE"

    /**
     * チケット 1 件分の Webhook 本文を組み立てる
     *
     * @param ticket 通知するチケット
     * @param category チケットのカテゴリー
     * @param avatarUrlTemplate アイコン画像 URL のテンプレート (config.conf の ticket.webhook.avatarUrl)
     * @return Discord の Execute Webhook API に送る JSON
     */
    fun create(ticket: Ticket, category: TicketCategory, avatarUrlTemplate: String): JsonObject = buildJsonObject {
        val avatarUrl = AvatarUrlTemplate.resolve(avatarUrlTemplate, ticket.playerName, ticket.playerUuid)
        putNoMentions()
        putJsonArray("embeds") {
            addJsonObject {
                putEmbed(
                    EmbedContent(
                        title = "Ticket Created - #${ticket.id}",
                        actorName = ticket.playerName,
                        avatarUrl = avatarUrl,
                        info = ticketInfo(ticket, category.name),
                        message = ticket.content,
                        footer = "Created by ${ticket.playerName}",
                        color = EMBED_COLOR,
                        timestamp = ticket.createdAt,
                    ),
                )
            }
        }
    }

    /**
     * チケットへのコメント 1 件分の Webhook 本文を組み立てる
     *
     * @param ticket コメント先のチケット
     * @param comment 通知するコメント
     * @param categoryName チケットのカテゴリーの表示名 (設定から削除されている場合は null で、id をそのまま表示する)
     * @param avatarUrlTemplate アイコン画像 URL のテンプレート (config.conf の ticket.webhook.avatarUrl)
     * @return Discord の Execute Webhook API に送る JSON
     */
    fun createComment(
        ticket: Ticket,
        comment: TicketComment,
        categoryName: String?,
        avatarUrlTemplate: String,
    ): JsonObject = buildJsonObject {
        // アイコンはチケットの送信者ではなく、コメントを書き込んだ人のものにする
        val avatarUrl = AvatarUrlTemplate.resolve(avatarUrlTemplate, comment.authorName, comment.authorUuid)
        putNoMentions()
        putJsonArray("embeds") {
            addJsonObject {
                putEmbed(
                    EmbedContent(
                        title = "Ticket Commented - #${ticket.id}",
                        actorName = comment.authorName,
                        avatarUrl = avatarUrl,
                        info = ticketInfo(ticket, categoryName ?: ticket.categoryId),
                        message = comment.content,
                        footer = "Commented by ${comment.authorName}",
                        color = COMMENT_EMBED_COLOR,
                        timestamp = comment.createdAt,
                    ),
                )
            }
        }
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
     * Embed 1 件分の項目を書き込む
     *
     * @param content Embed に表示する内容
     */
    private fun JsonObjectBuilder.putEmbed(content: EmbedContent) {
        putJsonObject("author") {
            put("name", content.actorName)
            content.avatarUrl?.let { put("icon_url", it) }
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
            put("text", content.footer)
            content.avatarUrl?.let { put("icon_url", it) }
        }
        put("timestamp", content.timestamp.toString())
    }

    /**
     * 本文に @everyone などが含まれていてもメンションが飛ばないようにする設定を追加する
     */
    private fun JsonObjectBuilder.putNoMentions() {
        putJsonObject("allowed_mentions") {
            putJsonArray("parse") {}
        }
    }

    /**
     * Embed に表示する内容 (新しいチケットとコメントで共通の形)
     *
     * @property title タイトル (操作内容とチケット番号)
     * @property actorName 操作したプレイヤーの名前
     * @property avatarUrl 操作したプレイヤーのアイコン画像の URL (表示しない場合は null)
     * @property info チケットの情報 (コードブロック)
     * @property message 本文
     * @property footer フッターの文言
     * @property color Embed の色
     * @property timestamp 操作日時
     */
    private data class EmbedContent(
        val title: String,
        val actorName: String,
        val avatarUrl: String?,
        val info: String,
        val message: String,
        val footer: String,
        val color: Int,
        val timestamp: Instant,
    )
}
