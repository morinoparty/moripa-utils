/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.velocity.model.announcement

import kotlinx.serialization.Serializable

/**
 * お知らせのメッセージファイル (message/<title>.json) の内容
 *
 * @property lines チャットに送る行 (MiniMessage 形式)。1 行以上でなければならない
 * @throws IllegalArgumentException lines が空の場合
 */
@Serializable
data class AnnouncementMessageFile(
    val lines: List<String>,
) {
    init {
        // 空のお知らせを送っても意味がないため、読み込み時に失敗させる
        require(lines.isNotEmpty()) { "lines must not be empty" }
    }
}
