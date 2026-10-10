/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.velocity.model.announcement

import net.kyori.adventure.text.Component

/**
 * 読み込み済みのお知らせ
 *
 * @property title お知らせの名前 (メッセージファイル名から拡張子を除いたもの)。ログに使う
 * @property content チャットに送る内容 (MiniMessage を変換済み)
 */
data class AnnouncementMessage(
    val title: String,
    val content: Component,
)
