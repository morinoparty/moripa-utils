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
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.config.TicketConfig
import party.morino.moripautils.common.ticket.TicketService
import java.time.Duration

/**
 * /ticket で表示するお問い合わせ用の Dialog を組み立てる (Factory)
 *
 * カテゴリーの選択肢 (single option) と本文 (複数行テキスト) の入力欄、送信 / キャンセルのボタンを持つ。
 * 送信ボタンのコールバックは 1 回しか使えないため、表示のたびに新しい Dialog を作ること。
 */
@Suppress("UnstableApiUsage")
class TicketDialogFactory : MoripaUtilsKoinComponent {
    private val config: TicketConfig by inject()
    private val submitHandler: TicketDialogSubmitHandler by inject()

    /**
     * お問い合わせ用の Dialog を新しく作る
     *
     * @return プレイヤーに showDialog で表示する Dialog
     */
    fun create(): Dialog {
        val base = DialogBase
            .builder(Component.text("お問い合わせ"))
            .canCloseWithEscape(true)
            // 送信 / キャンセルのどちらでも Dialog を閉じる (結果はチャットで伝える)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(listOf(DialogBody.plainMessage(Component.text("お問い合わせ内容を入力してください。"))))
            .inputs(listOf(createCategoryInput(), createContentInput()))
            .build()
        return Dialog.create { factory ->
            factory
                .empty()
                .base(base)
                .type(DialogType.confirmation(createSubmitButton(), createCancelButton()))
        }
    }

    /**
     * カテゴリーを選ぶ入力欄を作る (config.conf の ticket.categories の順に並べ、先頭を初期値にする)
     *
     * @return カテゴリーの単一選択の入力欄
     */
    private fun createCategoryInput(): DialogInput {
        val entries = config.categories.mapIndexed { index, category ->
            // 選択結果としてはカテゴリー id が返り、表示名は name を使う
            SingleOptionDialogInput.OptionEntry.create(category.id, Component.text(category.name), index == 0)
        }
        return DialogInput
            .singleOption(CATEGORY_KEY, Component.text("カテゴリー"), entries)
            .width(INPUT_WIDTH)
            .build()
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
     * 押されたときに入力値をコールバックで受け取り、[TicketDialogSubmitHandler] に渡す。
     * 二重送信を防ぐため、コールバックは 1 回だけ使えるようにする。
     *
     * @return 送信ボタン
     */
    private fun createSubmitButton(): ActionButton {
        val options = ClickCallback.Options
            .builder()
            .uses(1)
            .lifetime(CALLBACK_LIFETIME)
            .build()
        val action = DialogAction.customClick(
            DialogActionCallback { response, audience -> submitHandler.handle(response, audience) },
            options,
        )
        return ActionButton
            .builder(Component.text("送信"))
            .tooltip(Component.text("運営にお問い合わせを送信します"))
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
        /** カテゴリー入力欄のキー (送信時の値の取り出しに使う) */
        const val CATEGORY_KEY: String = "category"

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
