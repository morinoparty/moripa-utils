/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket.dialog

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.ticket.TicketService
import java.time.Duration

/**
 * /ticket comment <id> で表示するコメント入力用の Dialog を組み立てる (Factory)
 *
 * チャット欄は 256 文字までしか入力できないため、チケットの本文と同じ上限まで書けるよう Dialog で入力させる。
 * 送信ボタンのコールバックは 1 回しか使えないため、表示のたびに新しい Dialog を作ること。
 */
@Suppress("UnstableApiUsage")
class TicketCommentDialogFactory : MoripaUtilsKoinComponent {
    private val submitHandler: TicketCommentDialogSubmitHandler by inject()

    /**
     * コメント入力用の Dialog を新しく作る
     *
     * @param ticketId コメント先のチケットの id
     * @return プレイヤーに showDialog で表示する Dialog
     */
    fun create(ticketId: Long): Dialog {
        val base = DialogBase
            .builder(Component.text("お問い合わせ #$ticketId へのコメント"))
            .canCloseWithEscape(true)
            // 送信 / キャンセルのどちらでも Dialog を閉じる (結果はチャットで伝える)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(listOf(DialogBody.plainMessage(Component.text("コメントの内容を入力してください。"))))
            .inputs(listOf(createContentInput()))
            .build()
        return Dialog.create { factory ->
            factory
                .empty()
                .base(base)
                .type(DialogType.confirmation(createSubmitButton(ticketId), createCancelButton()))
        }
    }

    /**
     * 本文を入力する複数行のテキスト欄を作る
     *
     * @return 本文の入力欄 (最大文字数は TicketService の検証と同じ)
     */
    private fun createContentInput(): DialogInput = DialogInput
        .text(CONTENT_KEY, Component.text("内容"))
        .width(INPUT_WIDTH)
        // 既定の最大文字数 (32) では短すぎるため、サーバー側の上限に合わせる
        .maxLength(TicketService.MAX_CONTENT_LENGTH)
        // 行数は制限せず、文字数だけで制限する
        .multiline(TextDialogInput.MultilineOptions.create(null, CONTENT_HEIGHT))
        .build()

    /**
     * 送信ボタンを作る
     *
     * 二重送信を防ぐため、コールバックは 1 回だけ使えるようにする。
     *
     * @param ticketId コメント先のチケットの id (コールバックに閉じ込めて渡す)
     * @return 送信ボタン
     */
    private fun createSubmitButton(ticketId: Long): ActionButton {
        val options = ClickCallback.Options
            .builder()
            .uses(1)
            .lifetime(CALLBACK_LIFETIME)
            .build()
        val action = DialogAction.customClick(
            DialogActionCallback { response, audience -> submitHandler.handle(ticketId, response, audience) },
            options,
        )
        return ActionButton
            .builder(Component.text("送信"))
            .tooltip(Component.text("コメントを書き込みます"))
            .action(action)
            .build()
    }

    /**
     * キャンセルボタンを作る (アクションを持たず、押すと Dialog を閉じるだけ)
     *
     * @return キャンセルボタン
     */
    private fun createCancelButton(): ActionButton = ActionButton
        .builder(Component.text("キャンセル"))
        .build()

    companion object {
        /** 本文入力欄のキー (送信時の値の取り出しに使う) */
        const val CONTENT_KEY: String = "content"

        /** 入力欄の幅 (Dialog の最大幅に近い値) */
        private const val INPUT_WIDTH = 300

        /** 本文入力欄の高さ */
        private const val CONTENT_HEIGHT = 120

        /** 送信ボタンのコールバックが有効な時間 (開いたまま放置された Dialog から送れないようにする) */
        private val CALLBACK_LIFETIME: Duration = Duration.ofMinutes(30)
    }
}
