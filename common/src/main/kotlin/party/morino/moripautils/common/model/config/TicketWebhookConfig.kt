/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.model.config

import kotlinx.serialization.Serializable

/**
 * 新しいチケットを外部 (Discord など) へ通知する Webhook の設定
 *
 * @property url 通知先の Webhook URL。空文字の場合は通知しない
 * @property avatarUrl Embed に表示するプレイヤーのアイコン画像の URL。{uuid} と {name} はプレイヤーの UUID と名前に置き換える。
 *   空文字の場合はアイコンを表示しない
 */
@Serializable
data class TicketWebhookConfig(
    val url: String = "",
    val avatarUrl: String = DEFAULT_AVATAR_URL,
) {
    companion object {
        /** avatarUrl の既定値 (MC Heads の頭部画像。名前が変わっても同じ画像になるよう UUID で引く) */
        const val DEFAULT_AVATAR_URL: String = "https://api.mcheads.org/head/{uuid}/64"
    }
}
