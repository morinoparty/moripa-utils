/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.module
import party.morino.moripautils.common.database.MoripaUtilsDatabase
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.config.DatabaseConfig
import party.morino.moripautils.common.model.config.SqliteConfig
import party.morino.moripautils.common.model.ticket.TicketCommentAuthorType
import party.morino.moripautils.common.model.ticket.TicketCommentSubmission
import party.morino.moripautils.common.model.ticket.TicketLocation
import party.morino.moripautils.common.model.ticket.TicketSubmission
import party.morino.moripautils.common.ticket.database.ExposedTicketCommentRepository
import party.morino.moripautils.common.ticket.database.ExposedTicketRepository
import party.morino.moripautils.common.ticket.database.TicketDatabaseSchema
import java.nio.file.Path
import java.util.UUID

/**
 * [ExposedTicketCommentRepository] を一時ディレクトリの SQLite ファイル (共有データベース) で動かすテスト
 */
class ExposedTicketCommentRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var database: MoripaUtilsDatabase

    @BeforeEach
    fun setUp() {
        database = MoripaUtilsDatabase(DatabaseConfig(sqlite = SqliteConfig(file = "moripa-utils.db")), tempDir)
        MoripaUtilsKoinContext.start(
            listOf(
                module {
                    single { database }
                    single { TicketDatabaseSchema() }
                },
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
        MoripaUtilsKoinContext.stop()
    }

    @Test
    @DisplayName("Creates tables on first comment access and pages through comments")
    fun createAndList() = runBlocking {
        val comments = ExposedTicketCommentRepository()
        val ownerUuid = UUID.randomUUID()

        // 空のデータベースでコメント側から先に使っても、参照先の tickets テーブルごと作成される
        assertEquals(emptyList<Any>(), comments.listByTicket(1, null, 10))
        val ticket = ExposedTicketRepository().create(TicketSubmission("main", ownerUuid, "Alice", LOCATION, "bug", "hello"))

        val first = comments.create(
            TicketCommentSubmission(ticket.id, ownerUuid, "Alice", TicketCommentAuthorType.PLAYER, "first"),
        )
        val second = comments.create(
            TicketCommentSubmission(ticket.id, null, "bot", TicketCommentAuthorType.STAFF, "second"),
        )
        val third = comments.create(
            TicketCommentSubmission(ticket.id, ownerUuid, "Alice", TicketCommentAuthorType.PLAYER, "third"),
        )

        // 保存したものがそのまま、古い順にカーソル以降だけ返る (UUID が null の書き込みも読み戻せる)
        assertEquals(listOf(first, second, third), comments.listByTicket(ticket.id, null, 10))
        assertEquals(listOf(second, third), comments.listByTicket(ticket.id, first.id, 10))
        // 最新の件数分を古い順に並べ直して返す
        assertEquals(listOf(second, third), comments.listRecent(ticket.id, 2))
    }
}

/** テストで使う送信時の場所 */
private val LOCATION = TicketLocation("world_nether", 415, 32, -362)
