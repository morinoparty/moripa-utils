/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket

import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketCloseReason
import party.morino.moripautils.common.model.ticket.TicketListFilter
import party.morino.moripautils.common.model.ticket.TicketSearchQuery
import party.morino.moripautils.common.model.ticket.TicketStatus
import party.morino.moripautils.common.model.ticket.TicketSubmission
import java.util.UUID

/**
 * チケットの永続化を担うリポジトリ
 *
 * 実装はデータベースへの I/O をメインスレッド以外で行うこと (呼び出し側はどのスレッドからでも呼べる)。
 */
interface TicketRepository {
    /**
     * 新しいチケットを保存する
     *
     * @param submission 保存する内容
     * @return 採番された id と送信日時を含む、保存後のチケット (状態は OPEN)
     */
    suspend fun create(submission: TicketSubmission): Ticket

    /**
     * id でチケットを取得する
     *
     * @param id チケットの id
     * @return 見つかったチケット、存在しない場合は null
     */
    suspend fun findById(id: Long): Ticket?

    /**
     * 条件に一致するチケットを id の昇順で取得する
     *
     * @param query 検索条件
     * @return 最大 [TicketSearchQuery.limit] 件のチケット
     */
    suspend fun search(query: TicketSearchQuery): List<Ticket>

    /**
     * チケットを新しい順 (id の降順) に取得する
     *
     * /ticket list のページ表示や、id の Tab 補完に使う。
     *
     * @param playerUuid この送信者のチケットだけに絞り込む (すべてのチケットを対象にする場合は null)
     * @param filter 状態やプレイヤー名による絞り込み条件
     * @param offset 先頭から読み飛ばす件数
     * @param limit 取得する最大件数
     * @return 最大 [limit] 件のチケット (新しい順)
     */
    suspend fun listRecent(
        playerUuid: UUID?,
        filter: TicketListFilter,
        offset: Long,
        limit: Int,
    ): List<Ticket>

    /**
     * チケットの状態を変更する
     *
     * @param id チケットの id
     * @param status 変更後の状態
     * @param closeReason クローズした理由 (オープンに戻す場合は null)
     * @return 変更後のチケット、存在しない場合は null
     */
    suspend fun updateStatus(id: Long, status: TicketStatus, closeReason: TicketCloseReason?): Ticket?
}
