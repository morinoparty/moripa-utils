/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.ticket

import java.util.UUID

/**
 * 新しく保存するチケットの内容 (id / 状態 / 送信日時はリポジトリ側で決める)
 *
 * @property serverId 送信元のサーバー (config.conf の server)
 * @property playerUuid 送信したプレイヤーの UUID
 * @property playerName 送信時点のプレイヤー名
 * @property location 送信時にプレイヤーがいた場所
 * @property categoryId 検証済みのカテゴリー id
 * @property content 検証済みの本文 (前後の空白は取り除いてある)
 */
data class TicketSubmission(
    val serverId: String,
    val playerUuid: UUID,
    val playerName: String,
    val location: TicketLocation,
    val categoryId: String,
    val content: String,
)
