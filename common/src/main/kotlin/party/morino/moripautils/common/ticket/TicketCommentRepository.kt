/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket

import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketCommentSubmission

/**
 * チケットのコメントの永続化を担うリポジトリ
 *
 * 実装はデータベースへの I/O をメインスレッド以外で行うこと (呼び出し側はどのスレッドからでも呼べる)。
 * チケットの存在確認や権限の判定は呼び出し側 ([TicketService]) で行う。
 */
interface TicketCommentRepository {
    /**
     * 新しいコメントを保存する
     *
     * @param submission 保存する内容
     * @return 採番された id と書き込み日時を含む、保存後のコメント
     */
    suspend fun create(submission: TicketCommentSubmission): TicketComment

    /**
     * チケットのコメントを古い順 (id の昇順) に取得する
     *
     * @param ticketId チケットの id
     * @param afterId この id より後のコメントだけを返す (先頭から取得する場合は null)
     * @param limit 取得する最大件数 (1 以上)
     * @return 最大 [limit] 件のコメント
     */
    suspend fun listByTicket(ticketId: Long, afterId: Long?, limit: Int): List<TicketComment>

    /**
     * チケットの最新のコメントを取得する (ゲーム内でスレッドを表示するときに使う)
     *
     * @param ticketId チケットの id
     * @param limit 取得する最大件数 (1 以上)
     * @return 新しい方から最大 [limit] 件を、読みやすいよう古い順に並べ直したもの
     */
    suspend fun listRecent(ticketId: Long, limit: Int): List<TicketComment>
}
