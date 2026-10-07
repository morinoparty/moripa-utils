/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket

/**
 * ticket 機能で使う権限ノード (paper/build.gradle.kts の permissions と一致させること)
 */
object TicketPermissions {
    /** /ticket でお問い合わせを送信する権限 */
    const val USE: String = "moripautils.ticket.use"

    /** 新しいチケットやプレイヤーからのコメントの通知を受け取る権限 */
    const val NOTIFY: String = "moripautils.ticket.notify"

    /** すべてのチケットの閲覧やコメントを行うスタッフ向けの権限 */
    const val STAFF: String = "moripautils.ticket.staff"
}
