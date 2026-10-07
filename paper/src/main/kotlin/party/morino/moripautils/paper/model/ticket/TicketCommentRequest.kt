/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.model.ticket

import kotlinx.serialization.Serializable

/**
 * MineAuth の HTTP API でコメントを書き込むときのリクエスト本文
 *
 * @property content コメントの本文 (前後の空白は取り除いて保存する)
 */
@Serializable
data class TicketCommentRequest(
    val content: String,
)
