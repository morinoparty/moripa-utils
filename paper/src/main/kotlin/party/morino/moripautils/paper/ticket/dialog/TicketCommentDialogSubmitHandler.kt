/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket.dialog

import com.github.shynixn.mccoroutine.bukkit.launch
import io.papermc.paper.dialog.DialogResponseView
import kotlinx.coroutines.CancellationException
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.ticket.TicketCommentResult
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.ticket.TicketPermissions
import party.morino.moripautils.paper.ticket.toTicketActor
import java.util.logging.Level

/**
 * コメント入力 Dialog の送信ボタンが押されたときの処理
 *
 * 入力値を取り出して [TicketService] に渡し、結果をプレイヤーにチャットで伝える。
 */
@Suppress("UnstableApiUsage")
class TicketCommentDialogSubmitHandler : MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()
    private val service: TicketService by inject()

    /**
     * Dialog の送信を処理する
     *
     * コールバックは suspend ではないため、保存の待ち時間はコルーチンに逃がす (通知の完了は待たない)。
     *
     * @param ticketId コメント先のチケットの id
     * @param response Dialog の入力値
     * @param audience ボタンを押したプレイヤー
     */
    fun handle(ticketId: Long, response: DialogResponseView, audience: Audience) {
        // Dialog はプレイヤーにしか表示しないが、型の上では Audience なので確認する
        val player = audience as? Player ?: return
        // Dialog を開いた後に権限を外された場合に備えて、送信時にも確認する
        if (!player.hasPermission(TicketPermissions.USE)) {
            player.sendRichMessage("<red>コメントを書き込む権限がありません。")
            return
        }
        // 入力欄が欠けている (古い Dialog など) 場合は空文字として検証に任せる
        val content = response.getText(TicketCommentDialogFactory.CONTENT_KEY).orEmpty()
        // 権限はメインスレッドにいるうちに読み取っておく
        val actor = player.toTicketActor()

        plugin.launch {
            try {
                sendResult(player, service.addComment(actor, ticketId, content))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // DB の書き込み失敗など。プレイヤーには失敗だけを伝え、詳細はログに残す
                plugin.logger.log(Level.SEVERE, "Failed to add comment to ticket #$ticketId from ${player.name}", e)
                player.sendRichMessage("<red>コメントの書き込みに失敗しました。時間をおいて再度お試しください。")
            }
        }
    }

    /**
     * 書き込み結果をプレイヤーに伝える
     *
     * @param player 書き込んだプレイヤー
     * @param result 書き込み結果
     */
    private fun sendResult(player: Player, result: TicketCommentResult) {
        when (result) {
            is TicketCommentResult.Success -> player.sendRichMessage(
                "<green>お問い合わせ #<id> にコメントしました。",
                Placeholder.unparsed("id", result.ticket.id.toString()),
            )
            TicketCommentResult.TicketNotFound -> player.sendRichMessage(
                "<red>お問い合わせが見つかりません。",
            )
            TicketCommentResult.BlankContent -> player.sendRichMessage(
                "<red>コメントの内容を入力してください。",
            )
            is TicketCommentResult.ContentTooLong -> player.sendRichMessage(
                "<red>コメントは <max> 文字以内で入力してください。",
                Placeholder.unparsed("max", result.maxLength.toString()),
            )
        }
    }
}
