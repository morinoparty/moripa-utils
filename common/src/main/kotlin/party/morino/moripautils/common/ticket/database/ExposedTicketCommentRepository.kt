/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.core.component.inject
import party.morino.moripautils.common.database.MoripaUtilsDatabase
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketCommentSubmission
import party.morino.moripautils.common.ticket.TicketCommentRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 共有データベース ([MoripaUtilsDatabase]) の ticket_comments テーブルにコメントを保存するリポジトリ
 *
 * Exposed の DSL だけを使うため、SQLite / MySQL のどちらでも同じように動く。
 * テーブルは最初の操作時に [TicketDatabaseSchema] が (無ければ) 作成する。
 */
class ExposedTicketCommentRepository :
    TicketCommentRepository,
    MoripaUtilsKoinComponent {
    private val database: MoripaUtilsDatabase by inject()
    private val schema: TicketDatabaseSchema by inject()

    override suspend fun create(submission: TicketCommentSubmission): TicketComment = dbQuery {
        // DB によって timestamp の精度が異なるため、戻り値と DB の値がずれないようミリ秒に丸めておく
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val id = TicketCommentsTable.insert {
            it[ticketId] = submission.ticketId
            it[authorUuid] = submission.authorUuid?.toString()
            it[authorName] = submission.authorName
            it[authorType] = submission.authorType
            it[content] = submission.content
            it[TicketCommentsTable.createdAt] = createdAt
        } get TicketCommentsTable.id
        TicketComment(
            id = id,
            ticketId = submission.ticketId,
            authorUuid = submission.authorUuid,
            authorName = submission.authorName,
            authorType = submission.authorType,
            content = submission.content,
            createdAt = createdAt,
        )
    }

    override suspend fun listByTicket(ticketId: Long, afterId: Long?, limit: Int): List<TicketComment> = dbQuery {
        val statement = TicketCommentsTable
            .selectAll()
            .where { TicketCommentsTable.ticketId eq ticketId }
        // カーソルが指定されたときだけ、それより後に絞り込む
        afterId?.let { statement.andWhere { TicketCommentsTable.id greater it } }
        statement
            .orderBy(TicketCommentsTable.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toComment() }
    }

    override suspend fun listRecent(ticketId: Long, limit: Int): List<TicketComment> = dbQuery {
        TicketCommentsTable
            .selectAll()
            .where { TicketCommentsTable.ticketId eq ticketId }
            // 新しい方から取得し、表示用に古い順へ並べ直す
            .orderBy(TicketCommentsTable.id to SortOrder.DESC)
            .limit(limit)
            .map { it.toComment() }
            .reversed()
    }

    /**
     * テーブルを用意してからトランザクションを実行する
     *
     * @param T 処理の戻り値の型
     * @param block トランザクション内で実行する処理
     * @return 処理の結果
     */
    private suspend fun <T> dbQuery(block: JdbcTransaction.() -> T): T {
        schema.ensureCreated()
        return database.query(block)
    }

    /**
     * 取得した行をドメインモデルへ変換する
     *
     * @return 変換したコメント
     */
    private fun ResultRow.toComment(): TicketComment = TicketComment(
        id = this[TicketCommentsTable.id],
        ticketId = this[TicketCommentsTable.ticketId],
        authorUuid = this[TicketCommentsTable.authorUuid]?.let(UUID::fromString),
        authorName = this[TicketCommentsTable.authorName],
        authorType = this[TicketCommentsTable.authorType],
        content = this[TicketCommentsTable.content],
        createdAt = this[TicketCommentsTable.createdAt],
    )
}
