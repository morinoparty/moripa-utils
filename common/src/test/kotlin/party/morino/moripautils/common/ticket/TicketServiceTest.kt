/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.config.MoripaUtilsConfig
import party.morino.moripautils.common.model.config.TicketCategory
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketActor
import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketCommentAuthorType
import party.morino.moripautils.common.model.ticket.TicketCommentResult
import party.morino.moripautils.common.model.ticket.TicketCommentSubmission
import party.morino.moripautils.common.model.ticket.TicketSearchQuery
import party.morino.moripautils.common.model.ticket.TicketStatus
import party.morino.moripautils.common.model.ticket.TicketLocation
import party.morino.moripautils.common.model.ticket.TicketSubmission
import party.morino.moripautils.common.model.ticket.TicketSubmitResult
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * [TicketService] の分岐 (送信: カテゴリー不明 / 本文が空 / 成功、コメント: 権限なし / 本人 / 運営) を確認するテスト
 *
 * common のテストには mockk が無いため、リポジトリと通知先は記録するだけの手書きの偽物に差し替える。
 */
class TicketServiceTest {
    private val repository = RecordingRepository()
    private val commentRepository = RecordingCommentRepository()
    private val notifier = RecordingNotifier()
    // 通知は非同期に行われるため、完了を待てるように専用の Job を持つスコープを渡す
    private val notificationJob = Job()
    private val service = TicketService(Logger.getLogger("TicketServiceTest"), CoroutineScope(notificationJob))
    private val playerUuid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val config = MoripaUtilsConfig(server = "test")
        MoripaUtilsKoinContext.start(
            listOf(
                module {
                    single { config }
                    single<TicketRepository> { repository }
                    single<TicketCommentRepository> { commentRepository }
                    single { notifier } bind TicketNotifier::class
                },
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        service.close()
        MoripaUtilsKoinContext.stop()
    }

    @Test
    @DisplayName("Rejects an unknown category without saving or notifying")
    fun rejectsUnknownCategory() = runBlocking {
        val result = service.submit(playerUuid, "Steve", LOCATION, "unknown", "hello")

        assertEquals(TicketSubmitResult.UnknownCategory("unknown"), result)
        assertTrue(repository.created.isEmpty())
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    @DisplayName("Rejects blank content without saving or notifying")
    fun rejectsBlankContent() = runBlocking {
        val result = service.submit(playerUuid, "Steve", LOCATION, "bug", "   \n ")

        assertEquals(TicketSubmitResult.BlankContent, result)
        assertTrue(repository.created.isEmpty())
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    @DisplayName("Saves the trimmed ticket and notifies with the resolved category")
    fun savesAndNotifies() = runBlocking {
        val result = service.submit(playerUuid, "Steve", LOCATION, "bug", "  block disappeared  ")

        val success = assertInstanceOf(TicketSubmitResult.Success::class.java, result)
        // submit は通知の完了を待たずに返るため、起動された通知の完了を待ってから検証する
        notificationJob.children.toList().joinAll()
        // 送信元サーバーは config.conf の server、本文は前後の空白を取り除いたもの
        assertEquals(
            listOf(TicketSubmission("test", playerUuid, "Steve", LOCATION, "bug", "block disappeared")),
            repository.created,
        )
        assertEquals(listOf(success.ticket to "バグの報告"), notifier.notified.map { it.first to it.second.name })
    }

    @Test
    @DisplayName("Treats another player's ticket as not found when commenting")
    fun rejectsCommentFromStranger() = runBlocking {
        val ticket = repository.create(TicketSubmission("test", playerUuid, "Steve", LOCATION, "bug", "hello"))
        val stranger = TicketActor(UUID.randomUUID(), "Alex", isStaff = false)

        val result = service.addComment(stranger, ticket.id, "me too")

        assertEquals(TicketCommentResult.TicketNotFound, result)
        assertTrue(commentRepository.created.isEmpty())
    }

    @Test
    @DisplayName("Saves comments from the owner as PLAYER and from staff as STAFF")
    fun savesCommentsWithAuthorType() = runBlocking {
        val ticket = repository.create(TicketSubmission("test", playerUuid, "Steve", LOCATION, "bug", "hello"))
        val staff = TicketActor(null, "discord-bot", isStaff = true)

        service.addComment(TicketActor(playerUuid, "Steve", isStaff = false), ticket.id, "  more info  ")
        val result = service.addComment(staff, ticket.id, "we are checking")

        assertInstanceOf(TicketCommentResult.Success::class.java, result)
        notificationJob.children.toList().joinAll()
        // 本文は前後の空白を取り除き、立場は本人 / 運営で判定される
        assertEquals(
            listOf(
                TicketCommentSubmission(ticket.id, playerUuid, "Steve", TicketCommentAuthorType.PLAYER, "more info"),
                TicketCommentSubmission(ticket.id, null, "discord-bot", TicketCommentAuthorType.STAFF, "we are checking"),
            ),
            commentRepository.created,
        )
        assertEquals(2, notifier.commented.size)
    }

    @Test
    @DisplayName("Lists only own tickets for players and all tickets for staff")
    fun listsAccessibleTickets() = runBlocking {
        // 実在するプレイヤー (_NIKOMARU) の UUID を使う
        val nikomaruUuid = UUID.fromString("f8b761ec-4a54-48eb-a040-c5604042bcc9")
        val mine = repository.create(TicketSubmission("test", nikomaruUuid, "_NIKOMARU", "bug", "mine"))
        val others = repository.create(TicketSubmission("test", playerUuid, "Steve", "bug", "others"))

        val player = TicketActor(nikomaruUuid, "_NIKOMARU", isStaff = false)
        val staff = TicketActor(null, "discord-bot", isStaff = true)

        assertEquals(listOf(mine), service.listAccessibleTickets(player, 0, 10))
        // 運営はすべてのチケットを新しい順に閲覧できる
        assertEquals(listOf(others, mine), service.listAccessibleTickets(staff, 0, 10))
        // UUID を持たない運営以外の操作者は何も閲覧できない
        assertEquals(emptyList<Ticket>(), service.listAccessibleTickets(TicketActor(null, "bot", isStaff = false), 0, 10))
    }

    /** create の呼び出しを記録し、連番の id を振って返すリポジトリ */
    private class RecordingRepository : TicketRepository {
        val created = mutableListOf<TicketSubmission>()
        private val tickets = mutableListOf<Ticket>()

        override suspend fun create(submission: TicketSubmission): Ticket {
            created += submission
            val ticket = Ticket(
                id = created.size.toLong(),
                serverId = submission.serverId,
                playerUuid = submission.playerUuid,
                playerName = submission.playerName,
                location = submission.location,
                categoryId = submission.categoryId,
                content = submission.content,
                status = TicketStatus.OPEN,
                createdAt = Instant.EPOCH,
            )
            tickets += ticket
            return ticket
        }

        override suspend fun findById(id: Long): Ticket? = tickets.firstOrNull { it.id == id }

        override suspend fun search(query: TicketSearchQuery): List<Ticket> = emptyList()

        override suspend fun listRecent(playerUuid: UUID?, offset: Long, limit: Int): List<Ticket> = tickets
            .filter { playerUuid == null || it.playerUuid == playerUuid }
            .sortedByDescending { it.id }
            .drop(offset.toInt())
            .take(limit)
    }

    /** create の呼び出しを記録し、連番の id を振って返すコメントのリポジトリ */
    private class RecordingCommentRepository : TicketCommentRepository {
        val created = mutableListOf<TicketCommentSubmission>()

        override suspend fun create(submission: TicketCommentSubmission): TicketComment {
            created += submission
            return TicketComment(
                id = created.size.toLong(),
                ticketId = submission.ticketId,
                authorUuid = submission.authorUuid,
                authorName = submission.authorName,
                authorType = submission.authorType,
                content = submission.content,
                createdAt = Instant.EPOCH,
            )
        }

        override suspend fun listByTicket(ticketId: Long, afterId: Long?, limit: Int): List<TicketComment> = emptyList()

        override suspend fun listRecent(ticketId: Long, limit: Int): List<TicketComment> = emptyList()
    }

    /** 通知の呼び出しを記録するだけの通知先 */
    private class RecordingNotifier : TicketNotifier {
        val notified = mutableListOf<Pair<Ticket, TicketCategory>>()
        val commented = mutableListOf<TicketComment>()

        override suspend fun notify(ticket: Ticket, category: TicketCategory) {
            notified += ticket to category
        }

        override suspend fun notifyComment(ticket: Ticket, comment: TicketComment) {
            commented += comment
        }
    }
}

/** テストで使う送信時の場所 */
private val LOCATION = TicketLocation("world_nether", 415, 32, -362)
