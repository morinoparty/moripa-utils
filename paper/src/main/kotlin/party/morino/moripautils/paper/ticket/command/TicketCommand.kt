/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.ticket.command

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.ticket.TicketPermissions
import party.morino.moripautils.paper.ticket.dialog.TicketCommentDialogFactory
import party.morino.moripautils.paper.ticket.dialog.TicketDialogFactory
import party.morino.moripautils.paper.ticket.toTicketActor
import party.morino.moripautils.paper.ticket.view.TicketThreadPresenter

/**
 * /ticket コマンド (お問い合わせの送信 / 閲覧 / コメント)
 *
 * ブートストラップ段階で登録するため、生成時点では Koin コンテナがまだ存在しない。
 * 依存はコマンドの実行時にコンテナから取り出す
 * (by inject() で保持すると、プラグインの再有効化で作り直された古いインスタンスを使い続けてしまう)。
 */
@Suppress("UnstableApiUsage")
class TicketCommand {
    /**
     * お問い合わせ用の Dialog を開く
     *
     * @param source コマンドの実行元
     */
    @Command("ticket")
    @Permission(TicketPermissions.USE)
    @CommandDescription("運営にお問い合わせを送信します")
    suspend fun ticket(source: CommandSourceStack) {
        // Dialog はプレイヤーのクライアントにしか表示できない
        val player = requirePlayer(source) ?: return
        // プラグインの有効化に失敗した場合などはコンテナや定義が存在しない
        val koin = MoripaUtilsKoinContext.getOrNull()
        val plugin = koin?.getOrNull<MoripaUtils>()
        val dialogFactory = koin?.getOrNull<TicketDialogFactory>()
        if (plugin == null || dialogFactory == null) {
            player.sendRichMessage("<red>現在お問い合わせを受け付けていません。")
            return
        }
        // Cloud の非同期コーディネーターから呼ばれるため、Dialog の表示はメインスレッドで行う
        withContext(plugin.minecraftDispatcher) {
            player.showDialog(dialogFactory.create())
        }
    }

    /**
     * チケットと最新のコメントをチャットに表示する
     *
     * 本人は自分のチケットを、運営 (moripautils.ticket.staff) はすべてのチケットを表示できる。
     *
     * @param source コマンドの実行元
     * @param id チケットの id
     */
    @Command("ticket view <id>")
    @Permission(TicketPermissions.USE)
    @CommandDescription("お問い合わせとコメントを表示します")
    suspend fun view(
        source: CommandSourceStack,
        @Argument("id") id: Long,
    ) {
        val player = requirePlayer(source) ?: return
        val koin = MoripaUtilsKoinContext.getOrNull()
        val plugin = koin?.getOrNull<MoripaUtils>()
        val service = koin?.getOrNull<TicketService>()
        val presenter = koin?.getOrNull<TicketThreadPresenter>()
        if (plugin == null || service == null || presenter == null) {
            player.sendRichMessage("<red>現在お問い合わせを利用できません。")
            return
        }
        // 存在しないチケットと他人のチケットは区別せずに伝える
        val ticket = service.findAccessibleTicket(player.toTicketActor(), id)
        if (ticket == null) {
            player.sendRichMessage("<red>お問い合わせが見つかりません。")
            return
        }
        // 1 件多く取得して、表示しきれない古いコメントがあるかを判定する
        val fetched = service.listRecentComments(ticket.id, TicketThreadPresenter.MAX_COMMENTS + 1)
        val hasOlder = fetched.size > TicketThreadPresenter.MAX_COMMENTS
        val comments = fetched.takeLast(TicketThreadPresenter.MAX_COMMENTS)
        withContext(plugin.minecraftDispatcher) {
            presenter.present(player, ticket, comments, hasOlder)
        }
    }

    /**
     * チケットにコメントを書き込むための Dialog を開く
     *
     * 入力後に存在しないと分かるのを避けるため、Dialog を開く前にチケットを閲覧できるか確認する。
     *
     * @param source コマンドの実行元
     * @param id チケットの id
     */
    @Command("ticket comment <id>")
    @Permission(TicketPermissions.USE)
    @CommandDescription("お問い合わせにコメントを書き込みます")
    suspend fun comment(
        source: CommandSourceStack,
        @Argument("id") id: Long,
    ) {
        val player = requirePlayer(source) ?: return
        val koin = MoripaUtilsKoinContext.getOrNull()
        val plugin = koin?.getOrNull<MoripaUtils>()
        val service = koin?.getOrNull<TicketService>()
        val dialogFactory = koin?.getOrNull<TicketCommentDialogFactory>()
        if (plugin == null || service == null || dialogFactory == null) {
            player.sendRichMessage("<red>現在お問い合わせを利用できません。")
            return
        }
        if (service.findAccessibleTicket(player.toTicketActor(), id) == null) {
            player.sendRichMessage("<red>お問い合わせが見つかりません。")
            return
        }
        // Cloud の非同期コーディネーターから呼ばれるため、Dialog の表示はメインスレッドで行う
        withContext(plugin.minecraftDispatcher) {
            player.showDialog(dialogFactory.create(id))
        }
    }

    /**
     * 実行元がプレイヤーであることを確認する
     *
     * @param source コマンドの実行元
     * @return プレイヤー。プレイヤー以外の場合はメッセージを送って null を返す
     */
    private fun requirePlayer(source: CommandSourceStack): Player? {
        val sender = source.sender
        val player = sender as? Player
        if (player == null) {
            sender.sendRichMessage("<red>このコマンドはプレイヤーのみ実行できます。")
        }
        return player
    }
}
