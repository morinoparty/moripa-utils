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
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketLocation
import party.morino.moripautils.common.model.ticket.TicketSearchQuery
import party.morino.moripautils.common.model.ticket.TicketStatus
import party.morino.moripautils.common.model.ticket.TicketSubmission
import party.morino.moripautils.common.ticket.TicketRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 共有データベース ([MoripaUtilsDatabase]) の tickets テーブルにチケットを保存するリポジトリ
 *
 * Exposed の DSL だけを使うため、SQLite / MySQL のどちらでも同じように動く。
 * テーブルは最初の操作時に [TicketDatabaseSchema] が (無ければ) 作成する。
 */
class ExposedTicketRepository :
    TicketRepository,
    MoripaUtilsKoinComponent {
    private val database: MoripaUtilsDatabase by inject()
    private val schema: TicketDatabaseSchema by inject()

    override suspend fun create(submission: TicketSubmission): Ticket = dbQuery {
        // DB によって timestamp の精度が異なるため、戻り値と DB の値がずれないようミリ秒に丸めておく
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val id = TicketsTable.insert {
            it[serverId] = submission.serverId
            it[playerUuid] = submission.playerUuid.toString()
            it[playerName] = submission.playerName
            it[TicketsTable.world] = submission.location.world
            it[TicketsTable.blockX] = submission.location.x
            it[TicketsTable.blockY] = submission.location.y
            it[TicketsTable.blockZ] = submission.location.z
            it[categoryId] = submission.categoryId
            it[content] = submission.content
            it[status] = TicketStatus.OPEN
            it[TicketsTable.createdAt] = createdAt
        } get TicketsTable.id
        Ticket(
            id = id,
            serverId = submission.serverId,
            playerUuid = submission.playerUuid,
            playerName = submission.playerName,
            location = submission.location,
            categoryId = submission.categoryId,
            content = submission.content,
            status = TicketStatus.OPEN,
            createdAt = createdAt,
        )
    }

    override suspend fun findById(id: Long): Ticket? = dbQuery {
        TicketsTable
            .selectAll()
            .where { TicketsTable.id eq id }
            .singleOrNull()
            ?.toTicket()
    }

    override suspend fun search(query: TicketSearchQuery): List<Ticket> = dbQuery {
        val statement = TicketsTable.selectAll()
        // 指定された条件だけを AND で積み重ねる
        query.categoryId?.let { categoryId -> statement.andWhere { TicketsTable.categoryId eq categoryId } }
        query.status?.let { status -> statement.andWhere { TicketsTable.status eq status } }
        query.playerUuid?.let { uuid -> statement.andWhere { TicketsTable.playerUuid eq uuid.toString() } }
        query.afterId?.let { afterId -> statement.andWhere { TicketsTable.id greater afterId } }
        statement
            .orderBy(TicketsTable.id to SortOrder.ASC)
            .limit(query.limit)
            .map { it.toTicket() }
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
     * @return 変換したチケット
     */
    private fun ResultRow.toTicket(): Ticket = Ticket(
        id = this[TicketsTable.id],
        serverId = this[TicketsTable.serverId],
        playerUuid = UUID.fromString(this[TicketsTable.playerUuid]),
        playerName = this[TicketsTable.playerName],
        location = TicketLocation(
            world = this[TicketsTable.world],
            x = this[TicketsTable.blockX],
            y = this[TicketsTable.blockY],
            z = this[TicketsTable.blockZ],
        ),
        categoryId = this[TicketsTable.categoryId],
        content = this[TicketsTable.content],
        status = this[TicketsTable.status],
        createdAt = this[TicketsTable.createdAt],
    )
}
