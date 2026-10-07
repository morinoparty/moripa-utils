/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.ticket.webhook

/**
 * Discord Webhook の Embed 1 件に表示する内容 (新しいチケット / コメント / 状態の変更で共通の形)
 *
 * @property title タイトル (操作内容とチケット番号)
 * @property authorName 上部に表示する名前
 * @property authorIcon 上部に表示するアイコン画像の URL (表示しない場合は null)
 * @property info チケットの情報 (コードブロック)
 * @property message MESSAGE 欄に表示する本文
 * @property footerText フッターの文言
 * @property footerIcon フッターに表示するアイコン画像の URL (表示しない場合は null)
 * @property color Embed の色
 * @property timestamp 操作日時 (ISO-8601 形式)
 */
internal data class EmbedContent(
    val title: String,
    val authorName: String,
    val authorIcon: String?,
    val info: String,
    val message: String,
    val footerText: String,
    val footerIcon: String?,
    val color: Int,
    val timestamp: String,
)
