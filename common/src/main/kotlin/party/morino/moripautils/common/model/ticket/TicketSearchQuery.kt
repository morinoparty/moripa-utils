/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.ticket

import party.morino.moripautils.api.model.ticket.TicketStatus
import java.util.UUID

/**
 * チケット一覧の検索条件
 *
 * 結果は id の昇順で返し、[afterId] より大きい id だけを対象にするカーソル方式でページングする。
 * null の条件は絞り込みに使わない。
 *
 * @property categoryId 絞り込むカテゴリーの id
 * @property status 絞り込む対応状況
 * @property playerUuid 絞り込む送信者の UUID
 * @property afterId この id より後のチケットだけを返す (先頭から取得する場合は null)
 * @property limit 取得する最大件数 (1 以上)
 * @throws IllegalArgumentException limit が 1 未満の場合
 */
data class TicketSearchQuery(
    val categoryId: String? = null,
    val status: TicketStatus? = null,
    val playerUuid: UUID? = null,
    val afterId: Long? = null,
    val limit: Int,
) {
    init {
        // 0 件以下の取得は意味がなく、呼び出し側のバグなので早めに気付けるようにする
        require(limit >= 1) { "limit must be at least 1, but was $limit" }
    }
}
