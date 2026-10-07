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
 * チケット一覧 (/ticket list) の絞り込み条件
 *
 * @property status 対応状況で絞り込む (null ならすべて)
 * @property playerName 送信時点のプレイヤー名で絞り込む (null ならすべて、大文字小文字は区別しない)。
 *   共有データベースでは他サーバーのプレイヤーの UUID を引けないことがあるため、名前で絞り込む
 */
data class TicketListFilter(
    val status: TicketStatus? = null,
    val playerName: String? = null,
)
