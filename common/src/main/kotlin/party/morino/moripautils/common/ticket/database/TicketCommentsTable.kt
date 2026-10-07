/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import party.morino.moripautils.common.model.ticket.TicketCommentAuthorType

/**
 * チケットのコメントを保存する ticket_comments テーブルの定義
 *
 * 列名を変更すると既存のデータベースと互換性がなくなるため、変更する場合は移行処理を用意すること。
 */
object TicketCommentsTable : Table("ticket_comments") {
    /** 通し番号 (カーソル方式のページングにも使う) */
    val id = long("id").autoIncrement()

    /** コメント先のチケット (一覧はチケット単位で引くため index を張る) */
    val ticketId = reference("ticket_id", TicketsTable.id).index()

    /** 書き込んだプレイヤーの UUID (サービストークンからの書き込みでは null) */
    val authorUuid = varchar("author_uuid", UUID_LENGTH).nullable()

    /** 書き込んだ時点の名前 */
    val authorName = varchar("author_name", AUTHOR_NAME_LENGTH)

    /** 書き込んだ人の立場 (enum の名前で保存する) */
    val authorType = enumerationByName("author_type", AUTHOR_TYPE_LENGTH, TicketCommentAuthorType::class)

    /** 本文 */
    val content = text("content")

    /** 書き込み日時 */
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    /** UUID 文字列の長さ (ハイフン込み) */
    private const val UUID_LENGTH = 36

    /** 名前の最大長 (サービストークンのアカウント id も入るため、プレイヤー名より余裕を持たせる) */
    private const val AUTHOR_NAME_LENGTH = 128

    /** 立場の名前の最大長 */
    private const val AUTHOR_TYPE_LENGTH = 16
}
