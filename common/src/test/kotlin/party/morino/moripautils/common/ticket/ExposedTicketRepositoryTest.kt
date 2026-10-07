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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.module
import party.morino.moripautils.common.database.MoripaUtilsDatabase
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.config.DatabaseConfig
import party.morino.moripautils.common.model.config.SqliteConfig
import party.morino.moripautils.common.model.ticket.TicketSearchQuery
import party.morino.moripautils.common.model.ticket.TicketStatus
import party.morino.moripautils.common.model.ticket.TicketLocation
import party.morino.moripautils.common.model.ticket.TicketSubmission
import party.morino.moripautils.common.ticket.database.ExposedTicketRepository
import party.morino.moripautils.common.ticket.database.TicketDatabaseSchema
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * [ExposedTicketRepository] を一時ディレクトリの SQLite ファイル (共有データベース) で動かすテスト
 */
class ExposedTicketRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var database: MoripaUtilsDatabase

    @BeforeEach
    fun setUp() {
        // サブディレクトリが無くても作成されることも合わせて確認する
        database = MoripaUtilsDatabase(DatabaseConfig(sqlite = SqliteConfig(file = "data/moripa-utils.db")), tempDir)
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
    @DisplayName("Creates tickets and pages through them with filters")
    fun createAndSearch() = runBlocking {
        val repository = ExposedTicketRepository()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        val first = repository.create(TicketSubmission("main", alice, "Alice", LOCATION, "bug", "first"))
        val second = repository.create(TicketSubmission("main", bob, "Bob", LOCATION, "other", "second"))
        val third = repository.create(TicketSubmission("main", alice, "Alice", LOCATION, "bug", "third"))

        // 保存したものがそのまま読み出せる
        assertEquals(first, repository.findById(first.id))
        assertEquals(TicketStatus.OPEN, first.status)
        // カーソルより後だけが id の昇順で返る
        assertEquals(listOf(second, third), repository.search(TicketSearchQuery(afterId = first.id, limit = 10)))
        assertEquals(listOf(first), repository.search(TicketSearchQuery(limit = 1)))
        // カテゴリー / 送信者で絞り込める
        assertEquals(listOf(first, third), repository.search(TicketSearchQuery(categoryId = "bug", limit = 10)))
        assertEquals(listOf(second), repository.search(TicketSearchQuery(playerUuid = bob, limit = 10)))
        // 新しい順に、送信者で絞り込みつつページングできる
        assertEquals(listOf(third, second, first), repository.listRecent(null, 0, 10))
        assertEquals(listOf(first), repository.listRecent(alice, 1, 10))
        // 設定したファイルに tickets テーブルが作られている
        assertTrue(Files.exists(tempDir.resolve("data/moripa-utils.db")))
    }
}

/** テストで使う送信時の場所 */
private val LOCATION = TicketLocation("world_nether", 415, 32, -362)
