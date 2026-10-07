/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.ticket.webhook

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * config.conf の ticket.webhook.avatarUrl (アイコン画像 URL のテンプレート) を展開する
 *
 * 外部状態に依存しない純粋関数だけを持つ。
 */
object AvatarUrlTemplate {
    /** UUID に置き換えるプレースホルダー */
    private const val UUID_PLACEHOLDER = "{uuid}"

    /** 名前に置き換えるプレースホルダー */
    private const val NAME_PLACEHOLDER = "{name}"

    /**
     * テンプレートのプレースホルダーを置き換えて URL を作る
     *
     * @param template アイコン画像 URL のテンプレート
     * @param name プレイヤー名
     * @param uuid プレイヤーの UUID (サービストークンからのコメントなどでは null)
     * @return アイコン画像の URL。テンプレートが空の場合や、{uuid} を使うのに UUID が無い場合は null (アイコンを出さない)
     */
    fun resolve(template: String, name: String, uuid: UUID?): String? {
        if (template.isBlank()) {
            return null
        }
        // UUID が無い書き込み者に {uuid} を空文字で埋めると壊れた URL になるため、アイコン自体を出さない
        if (UUID_PLACEHOLDER in template && uuid == null) {
            return null
        }
        return template
            .replace(UUID_PLACEHOLDER, uuid?.toString().orEmpty())
            // 名前はパスに埋め込むため、念のため URL エンコードする (Bedrock 連携の接頭辞などに備える)
            .replace(NAME_PLACEHOLDER, URLEncoder.encode(name, StandardCharsets.UTF_8))
    }
}
