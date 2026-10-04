/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.ticket

/**
 * コメントを書き込んだ人の立場
 *
 * データベースには名前 ([name]) で保存するため、既存の値の名前は変更しないこと。
 */
enum class TicketCommentAuthorType {
    /** チケットを送信したプレイヤー本人 */
    PLAYER,

    /** 運営 (moripautils.ticket.staff 権限を持つプレイヤー、またはサービストークン) */
    STAFF,
}
