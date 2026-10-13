/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import party.morino.moripautils.api.model.ticket.TicketActor

/**
 * コマンドの実行元をチケットの操作者に変換する
 *
 * moripautils.ticket.staff 権限を持つ実行元は運営として、すべてのチケットを閲覧・操作できる。
 * コンソールなどプレイヤー以外は自分のチケットを持たないため、UUID を null にする
 * (コンソールは権限をすべて持つので運営として扱われる)。
 *
 * @return 操作者
 */
fun CommandSender.toTicketActor(): TicketActor = TicketActor(
    uuid = (this as? Player)?.uniqueId,
    name = name,
    isStaff = hasPermission(TicketPermissions.STAFF),
)
