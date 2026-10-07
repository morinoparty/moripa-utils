/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.model.ticket

import kotlinx.serialization.Serializable

/**
 * MineAuth の HTTP API で返すコメント一覧のレスポンス (カーソル方式のページング)
 *
 * @property comments id の昇順 (古い順) に並んだコメント
 * @property nextCursor 次のページを取得するときに cursor へ渡す値 (次のページが無ければ null)
 * @property hasMore 次のページがあるかどうか
 */
@Serializable
data class TicketCommentListResponse(
    val comments: List<TicketCommentResponse>,
    val nextCursor: Long?,
    val hasMore: Boolean,
)
