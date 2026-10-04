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
 * チケットを操作 (閲覧 / コメント) しようとしている人
 *
 * UI や HTTP API の認証結果をこの形にまとめ、[party.morino.moripautils.common.ticket.TicketService] で権限を判定する。
 *
 * @property uuid プレイヤーの UUID (サービストークンの場合は null)
 * @property name 表示名 (コメントの書き込み者名として保存する)
 * @property isStaff 運営としてすべてのチケットを扱えるかどうか
 */
data class TicketActor(
    val uuid: UUID?,
    val name: String,
    val isStaff: Boolean,
)
