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
import party.morino.moripautils.common.model.ticket.TicketLocation
import party.morino.moripautils.common.model.ticket.TicketSubmitResult
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.ticket.TicketPermissions
import java.util.logging.Level

/**
 * お問い合わせ Dialog の送信ボタンが押されたときの処理
 *
 * 入力値を取り出して [TicketService] に渡し、結果をプレイヤーにチャットで伝える。
 */
@Suppress("UnstableApiUsage")
class TicketDialogSubmitHandler : MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()
    private val service: TicketService by inject()

    /**
     * Dialog の送信を処理する
     *
     * コールバックは suspend ではないため、保存の待ち時間はコルーチンに逃がす (通知の完了は待たない)。
     * コルーチンはメインスレッドのディスパッチャーで動き、DB 操作だけが I/O スレッドで行われる。
     *
     * @param response Dialog の入力値
     * @param audience ボタンを押したプレイヤー
     */
    fun handle(response: DialogResponseView, audience: Audience) {
        // Dialog はプレイヤーにしか表示しないが、型の上では Audience なので確認する
        val player = audience as? Player ?: return
        // Dialog を開いた後に権限を外された場合に備えて、送信時にも確認する
        if (!player.hasPermission(TicketPermissions.USE)) {
            player.sendRichMessage("<red>お問い合わせを送信する権限がありません。")
            return
        }
        // 入力欄が欠けている (古い Dialog など) 場合は空文字として検証に任せる
        val categoryId = response.getText(TicketDialogFactory.CATEGORY_KEY).orEmpty()
        val content = response.getText(TicketDialogFactory.CONTENT_KEY).orEmpty()
        // 運営が現地を確認できるよう、送信した瞬間の場所を記録する (コルーチンに入る前に読み取る)
        val location = player.location.let { TicketLocation(it.world.name, it.blockX, it.blockY, it.blockZ) }

        plugin.launch {
            try {
                sendResult(player, service.submit(player.uniqueId, player.name, location, categoryId, content))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // DB の書き込み失敗など。プレイヤーには失敗だけを伝え、詳細はログに残す
                plugin.logger.log(Level.SEVERE, "Failed to submit ticket from ${player.name}", e)
                player.sendRichMessage("<red>お問い合わせの送信に失敗しました。時間をおいて再度お試しください。")
            }
        }
    }

    /**
     * 送信結果をプレイヤーに伝える
     *
     * @param player 送信したプレイヤー
     * @param result 送信結果
     */
    private fun sendResult(player: Player, result: TicketSubmitResult) {
        when (result) {
            is TicketSubmitResult.Success -> player.sendRichMessage(
                "<green>お問い合わせを受け付けました。</green> <gray>(チケット番号: #<id>)",
                Placeholder.unparsed("id", result.ticket.id.toString()),
            )
            is TicketSubmitResult.UnknownCategory -> player.sendRichMessage(
                "<red>選択されたカテゴリーは現在使用できません。/ticket からやり直してください。",
            )
            TicketSubmitResult.BlankContent -> player.sendRichMessage(
                "<red>お問い合わせ内容を入力してください。/ticket からやり直してください。",
            )
            is TicketSubmitResult.ContentTooLong -> player.sendRichMessage(
                "<red>お問い合わせ内容は <max> 文字以内で入力してください。",
                Placeholder.unparsed("max", result.maxLength.toString()),
            )
        }
    }
}
