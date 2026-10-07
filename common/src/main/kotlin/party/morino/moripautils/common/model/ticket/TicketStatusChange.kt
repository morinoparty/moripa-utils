/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.model.ticket

import java.time.Instant

/**
 * チケットの状態の変更 (クローズ / 再オープン)
 *
 * @property ticket 変更後のチケット (クローズの場合は [Ticket.closeReason] に理由が入る)
 * @property actor 変更した人
 * @property changedAt 変更した日時
 */
data class TicketStatusChange(
    val ticket: Ticket,
    val actor: TicketActor,
    val changedAt: Instant,
)
