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
import org.incendo.cloud.annotation.specifier.Range
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.ticket.TicketPermissions
import party.morino.moripautils.paper.ticket.dialog.TicketCommentDialogFactory
import party.morino.moripautils.paper.ticket.dialog.TicketDialogFactory
import party.morino.moripautils.paper.ticket.toTicketActor
import party.morino.moripautils.paper.ticket.view.TicketListPresenter
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
        @Argument(value = "id", suggestions = TICKET_ID_SUGGESTIONS) id: Long,
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
        @Argument(value = "id", suggestions = TICKET_ID_SUGGESTIONS) id: Long,
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
     * 閲覧できるチケットを新しい順に一覧表示する
     *
     * 本人は自分のチケットを、運営 (moripautils.ticket.staff) はすべてのチケットを表示できる。
     *
     * @param source コマンドの実行元
     * @param page 表示するページ番号 (1 始まり、省略時は 1)
     */
    @Command("ticket list [page]")
    @Permission(TicketPermissions.USE)
    @CommandDescription("お問い合わせの一覧を表示します")
    suspend fun list(
        source: CommandSourceStack,
        @Argument("page") @Default("1") @Range(min = "1") page: Int,
    ) {
        val player = requirePlayer(source) ?: return
        val koin = MoripaUtilsKoinContext.getOrNull()
        val plugin = koin?.getOrNull<MoripaUtils>()
        val service = koin?.getOrNull<TicketService>()
        val presenter = koin?.getOrNull<TicketListPresenter>()
        if (plugin == null || service == null || presenter == null) {
            player.sendRichMessage("<red>現在お問い合わせを利用できません。")
            return
        }
        val pageSize = TicketListPresenter.PAGE_SIZE
        val offset = (page - 1).toLong() * pageSize
        // 1 件多く取得して、次のページがあるかを判定する
        val fetched = service.listAccessibleTickets(player.toTicketActor(), offset, pageSize + 1)
        val hasNext = fetched.size > pageSize
        withContext(plugin.minecraftDispatcher) {
            presenter.present(player, fetched.take(pageSize), page, hasNext)
        }
    }

    /**
     * チケット id の Tab 補完候補を返す
     *
     * 実行元が閲覧できるチケット (本人は自分のもの、運営はすべて) だけを新しい順に返す。
     * 入力途中の文字列による絞り込みは Cloud が行う。
     *
     * Cloud は suspend の補完メソッドに (コンテキスト, 入力) の 2 引数を要求するため、使わない入力も受け取る
     * (引数が足りないとブートストラップ時の登録で例外になり、プラグインが読み込まれない)。
     *
     * @param context コマンドの実行コンテキスト
     * @param input 入力途中の文字列 (絞り込みは Cloud が行うため使わない)
     * @return 補完候補のチケット id
     */
    @Suggestions(TICKET_ID_SUGGESTIONS)
    @Suppress("UnusedParameter")
    suspend fun suggestTicketIds(context: CommandContext<CommandSourceStack>, input: String): List<String> {
        // コンソールなどプレイヤー以外は自分のチケットを持たない
        val player = context.sender().sender as? Player ?: return emptyList()
        // 無効化中などでサービスが無い場合は候補を出さない
        val service = MoripaUtilsKoinContext.getOrNull()?.getOrNull<TicketService>() ?: return emptyList()
        return service
            .listAccessibleTickets(player.toTicketActor(), 0, MAX_SUGGESTIONS)
            .map { it.id.toString() }
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

    companion object {
        /** チケット id の Tab 補完に使うサジェストプロバイダーの名前 */
        const val TICKET_ID_SUGGESTIONS: String = "ticket-ids"

        /** Tab 補完に出すチケット id の最大件数 (多すぎると候補の一覧が読めなくなる) */
        private const val MAX_SUGGESTIONS: Int = 30
    }
}
