/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Server
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.TicketCategory
import party.morino.moripautils.common.model.ticket.Ticket
import party.morino.moripautils.common.model.ticket.TicketComment
import party.morino.moripautils.common.model.ticket.TicketCommentAuthorType
import party.morino.moripautils.common.model.ticket.TicketStatusChange
import party.morino.moripautils.common.ticket.TicketNotifier
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.ticket.view.TicketStatusLabel
import java.util.logging.Level

/**
 * チケットの動きをオンラインのプレイヤーへチャットで知らせる通知先
 *
 * - 新しいチケット / プレイヤーからのコメント: 運営 (moripautils.ticket.staff) へ
 * - 運営からのコメント: チケットを送信した本人へ (このサーバーにオンラインの場合のみ)
 * - 本人によるクローズ: 運営へ
 * - 運営によるクローズ / 再オープン: チケットを送信した本人へ (このサーバーにオンラインの場合のみ)
 */
class InGameTicketNotifier :
    TicketNotifier,
    MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()
    private val server: Server by inject()

    override suspend fun notify(ticket: Ticket, category: TicketCategory) {
        try {
            // オンラインプレイヤーの一覧や権限はメインスレッドで読む
            withContext(plugin.minecraftDispatcher) {
                server.onlinePlayers
                    .filter { it.hasPermission(TicketPermissions.STAFF) }
                    .forEach { staff ->
                        // プレイヤー名やカテゴリー名に MiniMessage のタグが含まれていても解釈させない
                        staff.sendRichMessage(
                            "<gold>[Ticket]</gold> <player> さんから新しいお問い合わせがあります " +
                                "<gray>(#<id> / <category> / <server>)",
                            Placeholder.unparsed("player", ticket.playerName),
                            Placeholder.unparsed("id", ticket.id.toString()),
                            Placeholder.unparsed("category", category.name),
                            Placeholder.unparsed("server", ticket.serverId),
                        )
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 通知の失敗でチケットの送信を失敗させない
            plugin.logger.log(Level.WARNING, "Failed to notify staff of ticket #${ticket.id}", e)
        }
    }

    override suspend fun notifyComment(ticket: Ticket, comment: TicketComment) {
        try {
            // オンラインプレイヤーの一覧や権限はメインスレッドで読む
            withContext(plugin.minecraftDispatcher) {
                when (comment.authorType) {
                    TicketCommentAuthorType.PLAYER -> notifyStaffOfComment(ticket, comment)
                    TicketCommentAuthorType.STAFF -> notifyOwnerOfReply(ticket)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 通知の失敗でコメントの書き込みを失敗させない
            plugin.logger.log(Level.WARNING, "Failed to notify comment #${comment.id} on ticket #${ticket.id}", e)
        }
    }

    override suspend fun notifyStatusChange(change: TicketStatusChange) {
        val ticket = change.ticket
        try {
            // オンラインプレイヤーの一覧や権限はメインスレッドで読む
            withContext(plugin.minecraftDispatcher) {
                if (change.actor.uuid == ticket.playerUuid) {
                    // 本人が自分で閉じた場合は、対応中の運営に知らせる
                    notifyStaffOfStatusChange(change)
                } else {
                    // 運営が変更した場合は、送信した本人に結果を知らせる
                    notifyOwnerOfStatusChange(change)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 通知の失敗で状態の変更を失敗させない
            plugin.logger.log(Level.WARNING, "Failed to notify status change of ticket #${ticket.id}", e)
        }
    }

    /**
     * 本人による状態の変更を、運営へ知らせる (メインスレッドで呼ぶこと)
     *
     * @param change 状態の変更
     */
    private fun notifyStaffOfStatusChange(change: TicketStatusChange) {
        val ticket = change.ticket
        server.onlinePlayers
            // 変更した本人には知らせない
            .filter { it.hasPermission(TicketPermissions.STAFF) && it.uniqueId != change.actor.uuid }
            .forEach { staff ->
                staff.sendRichMessage(
                    "<gold>[Ticket]</gold> <player> さんがお問い合わせ #<id> を「<status>」にしました " +
                        "<click:run_command:'/ticket view ${ticket.id}'><aqua>[表示]</aqua></click>",
                    Placeholder.unparsed("player", change.actor.name),
                    Placeholder.unparsed("id", ticket.id.toString()),
                    Placeholder.unparsed("status", TicketStatusLabel.of(ticket)),
                )
            }
    }

    /**
     * 運営による状態の変更を、チケットを送信した本人へ知らせる (メインスレッドで呼ぶこと)
     *
     * 共有データベースを使っていても、通知できるのはこのサーバーにオンラインの場合だけ。
     *
     * @param change 状態の変更
     */
    private fun notifyOwnerOfStatusChange(change: TicketStatusChange) {
        val ticket = change.ticket
        val owner = server.getPlayer(ticket.playerUuid) ?: return
        owner.sendRichMessage(
            "<gold>[Ticket]</gold> お問い合わせ #<id> が「<status>」になりました " +
                "<click:run_command:'/ticket view ${ticket.id}'><aqua>[表示]</aqua></click>",
            Placeholder.unparsed("id", ticket.id.toString()),
            Placeholder.unparsed("status", TicketStatusLabel.of(ticket)),
        )
    }

    /**
     * プレイヤーからのコメントを、運営へ知らせる (メインスレッドで呼ぶこと)
     *
     * @param ticket コメント先のチケット
     * @param comment 書き込まれたコメント
     */
    private fun notifyStaffOfComment(ticket: Ticket, comment: TicketComment) {
        server.onlinePlayers
            // 書き込んだ本人には知らせない
            .filter { it.hasPermission(TicketPermissions.STAFF) && it.uniqueId != comment.authorUuid }
            .forEach { staff ->
                staff.sendRichMessage(
                    "<gold>[Ticket]</gold> <player> さんがお問い合わせ #<id> にコメントしました " +
                        "<click:run_command:'/ticket view ${ticket.id}'><aqua>[表示]</aqua></click>",
                    Placeholder.unparsed("player", comment.authorName),
                    Placeholder.unparsed("id", ticket.id.toString()),
                )
            }
    }

    /**
     * 運営からの返信を、チケットを送信した本人へ知らせる (メインスレッドで呼ぶこと)
     *
     * 共有データベースを使っていても、通知できるのはこのサーバーにオンラインの場合だけ。
     *
     * @param ticket 返信されたチケット
     */
    private fun notifyOwnerOfReply(ticket: Ticket) {
        val owner = server.getPlayer(ticket.playerUuid) ?: return
        owner.sendRichMessage(
            "<gold>[Ticket]</gold> お問い合わせ #<id> に運営から返信がありました " +
                "<click:run_command:'/ticket view ${ticket.id}'><aqua>[表示]</aqua></click>",
            Placeholder.unparsed("id", ticket.id.toString()),
        )
    }
}
