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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Location
import org.bukkit.entity.Player
import org.incendo.cloud.annotation.specifier.Range
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Flag
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.config.MoripaUtilsConfig
import party.morino.moripautils.common.model.ticket.TicketCloseReason
import party.morino.moripautils.common.model.ticket.TicketListFilter
import party.morino.moripautils.common.model.ticket.TicketStatus
import party.morino.moripautils.common.model.ticket.TicketStatusChangeResult
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.ticket.TicketPermissions
import party.morino.moripautils.paper.ticket.dialog.TicketCommentDialogFactory
import party.morino.moripautils.paper.ticket.dialog.TicketDialogFactory
import party.morino.moripautils.paper.ticket.toTicketActor
import party.morino.moripautils.paper.ticket.view.TicketListPresenter
import party.morino.moripautils.paper.ticket.view.TicketStatusLabel
import party.morino.moripautils.paper.ticket.view.TicketThreadPresenter

/**
 * /ticket コマンド (お問い合わせの送信 / 閲覧 / コメント / 一覧 / クローズ / 再オープン / テレポート)
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
     * お問い合わせ用の Dialog を開く (/ticket と同じ)
     *
     * @param source コマンドの実行元
     */
    @Command("ticket create")
    @Permission(TicketPermissions.USE)
    @CommandDescription("運営にお問い合わせを送信します")
    suspend fun create(source: CommandSourceStack) {
        ticket(source)
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
     * @param status 対応状況で絞り込む (open / closed)
     * @param playerName 送信したプレイヤー名で絞り込む (運営のみ)
     */
    @Command("ticket list [page]")
    @Permission(TicketPermissions.USE)
    @CommandDescription("お問い合わせの一覧を表示します")
    suspend fun list(
        source: CommandSourceStack,
        @Argument("page") @Default("1") @Range(min = "1") page: Int,
        @Flag("status") status: TicketStatus?,
        @Flag(value = "player", suggestions = ONLINE_PLAYER_SUGGESTIONS, permission = TicketPermissions.STAFF)
        playerName: String?,
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
        val filter = TicketListFilter(status = status, playerName = playerName)
        val pageSize = TicketListPresenter.PAGE_SIZE
        val offset = (page - 1).toLong() * pageSize
        // 1 件多く取得して、次のページがあるかを判定する
        val fetched = service.listAccessibleTickets(player.toTicketActor(), filter, offset, pageSize + 1)
        val hasNext = fetched.size > pageSize
        withContext(plugin.minecraftDispatcher) {
            presenter.present(player, fetched.take(pageSize), page, hasNext, filter)
        }
    }

    /**
     * チケットをクローズする
     *
     * 本人は自分のチケットを、運営 (moripautils.ticket.staff) はすべてのチケットをクローズできる。
     *
     * @param source コマンドの実行元
     * @param id チケットの id
     * @param reasonType クローズする理由 (done / not-planned / duplicate、省略時は done)
     */
    @Command("ticket close <id>")
    @Permission(TicketPermissions.USE)
    @CommandDescription("お問い合わせをクローズします")
    suspend fun close(
        source: CommandSourceStack,
        @Argument(value = "id", suggestions = OPEN_TICKET_ID_SUGGESTIONS) id: Long,
        @Flag(value = "reason-type", suggestions = CLOSE_REASON_SUGGESTIONS) reasonType: String?,
    ) {
        val player = requirePlayer(source) ?: return
        val service = MoripaUtilsKoinContext.getOrNull()?.getOrNull<TicketService>()
        if (service == null) {
            player.sendRichMessage("<red>現在お問い合わせを利用できません。")
            return
        }
        // 補完候補以外の値が入力された場合は、使える値を案内する
        val reason = if (reasonType == null) TicketCloseReason.DEFAULT else TicketCloseReason.fromId(reasonType)
        if (reason == null) {
            player.sendRichMessage(
                "<red>理由には <reasons> のいずれかを指定してください。",
                Placeholder.unparsed("reasons", TicketCloseReason.entries.joinToString(" / ") { it.id }),
            )
            return
        }
        sendStatusChangeResult(player, id, service.closeTicket(player.toTicketActor(), id, reason))
    }

    /**
     * クローズされたチケットをオープンに戻す (運営のみ)
     *
     * @param source コマンドの実行元
     * @param id チケットの id
     */
    @Command("ticket reopen <id>")
    @Permission(TicketPermissions.STAFF)
    @CommandDescription("クローズしたお問い合わせを再オープンします")
    suspend fun reopen(
        source: CommandSourceStack,
        @Argument(value = "id", suggestions = CLOSED_TICKET_ID_SUGGESTIONS) id: Long,
    ) {
        val player = requirePlayer(source) ?: return
        val service = MoripaUtilsKoinContext.getOrNull()?.getOrNull<TicketService>()
        if (service == null) {
            player.sendRichMessage("<red>現在お問い合わせを利用できません。")
            return
        }
        sendStatusChangeResult(player, id, service.reopenTicket(player.toTicketActor(), id))
    }

    /**
     * チケットを送信した場所へテレポートする (運営のみ)
     *
     * 共有データベースでは他のサーバーのチケットも見えるため、このサーバーで送信されたチケットだけを対象にする。
     *
     * @param source コマンドの実行元
     * @param id チケットの id
     */
    @Command("ticket teleport|tp <id>")
    @Permission(TicketPermissions.STAFF)
    @CommandDescription("お問い合わせを送信した場所へテレポートします")
    suspend fun teleport(
        source: CommandSourceStack,
        @Argument(value = "id", suggestions = TICKET_ID_SUGGESTIONS) id: Long,
    ) {
        val player = requirePlayer(source) ?: return
        val koin = MoripaUtilsKoinContext.getOrNull()
        val plugin = koin?.getOrNull<MoripaUtils>()
        val service = koin?.getOrNull<TicketService>()
        val config = koin?.getOrNull<MoripaUtilsConfig>()
        if (plugin == null || service == null || config == null) {
            player.sendRichMessage("<red>現在お問い合わせを利用できません。")
            return
        }
        val ticket = service.findAccessibleTicket(player.toTicketActor(), id)
        if (ticket == null) {
            player.sendRichMessage("<red>お問い合わせが見つかりません。")
            return
        }
        if (ticket.serverId != config.server) {
            player.sendRichMessage(
                "<red>このお問い合わせは別のサーバー (<server>) で送信されました。",
                Placeholder.unparsed("server", ticket.serverId),
            )
            return
        }
        // ワールドの取得とテレポートはメインスレッドで行う
        withContext(plugin.minecraftDispatcher) {
            val location = ticket.location
            val world = plugin.server.getWorld(location.world)
            if (world == null) {
                player.sendRichMessage(
                    "<red>ワールド <world> が見つかりません。",
                    Placeholder.unparsed("world", location.world),
                )
                return@withContext
            }
            // ブロックの中央に立たせる
            player.teleportAsync(Location(world, location.x + BLOCK_CENTER, location.y.toDouble(), location.z + BLOCK_CENTER))
            player.sendRichMessage(
                "<green>お問い合わせ #<id> を送信した場所へテレポートしました。",
                Placeholder.unparsed("id", ticket.id.toString()),
            )
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
    suspend fun suggestTicketIds(context: CommandContext<CommandSourceStack>, input: String): List<String> =
        suggestAccessibleTicketIds(context, TicketListFilter())

    /**
     * オープン中のチケット id の Tab 補完候補を返す (/ticket close 用)
     *
     * @param context コマンドの実行コンテキスト
     * @param input 入力途中の文字列 (絞り込みは Cloud が行うため使わない)
     * @return 補完候補のチケット id
     */
    @Suggestions(OPEN_TICKET_ID_SUGGESTIONS)
    @Suppress("UnusedParameter")
    suspend fun suggestOpenTicketIds(context: CommandContext<CommandSourceStack>, input: String): List<String> =
        suggestAccessibleTicketIds(context, TicketListFilter(status = TicketStatus.OPEN))

    /**
     * クローズされたチケット id の Tab 補完候補を返す (/ticket reopen 用)
     *
     * @param context コマンドの実行コンテキスト
     * @param input 入力途中の文字列 (絞り込みは Cloud が行うため使わない)
     * @return 補完候補のチケット id
     */
    @Suggestions(CLOSED_TICKET_ID_SUGGESTIONS)
    @Suppress("UnusedParameter")
    suspend fun suggestClosedTicketIds(context: CommandContext<CommandSourceStack>, input: String): List<String> =
        suggestAccessibleTicketIds(context, TicketListFilter(status = TicketStatus.CLOSED))

    /**
     * クローズする理由の Tab 補完候補を返す
     *
     * @param context コマンドの実行コンテキスト
     * @param input 入力途中の文字列 (絞り込みは Cloud が行うため使わない)
     * @return 補完候補の理由の識別子
     */
    @Suggestions(CLOSE_REASON_SUGGESTIONS)
    @Suppress("UnusedParameter")
    fun suggestCloseReasons(context: CommandContext<CommandSourceStack>, input: String): List<String> =
        TicketCloseReason.entries.map { it.id }

    /**
     * オンラインのプレイヤー名の Tab 補完候補を返す (/ticket list --player 用)
     *
     * @param context コマンドの実行コンテキスト
     * @param input 入力途中の文字列 (絞り込みは Cloud が行うため使わない)
     * @return 補完候補のプレイヤー名
     */
    @Suggestions(ONLINE_PLAYER_SUGGESTIONS)
    @Suppress("UnusedParameter")
    fun suggestOnlinePlayers(context: CommandContext<CommandSourceStack>, input: String): List<String> =
        context.sender().sender.server.onlinePlayers.map { it.name }

    /**
     * 実行元が閲覧できるチケットの id を新しい順に返す
     *
     * @param context コマンドの実行コンテキスト
     * @param filter 絞り込み条件
     * @return 補完候補のチケット id
     */
    private suspend fun suggestAccessibleTicketIds(
        context: CommandContext<CommandSourceStack>,
        filter: TicketListFilter,
    ): List<String> {
        // コンソールなどプレイヤー以外は自分のチケットを持たない
        val player = context.sender().sender as? Player ?: return emptyList()
        // 無効化中などでサービスが無い場合は候補を出さない
        val service = MoripaUtilsKoinContext.getOrNull()?.getOrNull<TicketService>() ?: return emptyList()
        return service
            .listAccessibleTickets(player.toTicketActor(), filter, 0, MAX_SUGGESTIONS)
            .map { it.id.toString() }
    }

    /**
     * クローズ / 再オープンの結果をプレイヤーに伝える
     *
     * @param player 実行したプレイヤー
     * @param id 指定されたチケットの id
     * @param result 変更結果
     */
    private fun sendStatusChangeResult(player: Player, id: Long, result: TicketStatusChangeResult) {
        val idPlaceholder = Placeholder.unparsed("id", id.toString())
        when (result) {
            is TicketStatusChangeResult.Success -> player.sendRichMessage(
                "<green>お問い合わせ #<id> を「<status>」にしました。",
                idPlaceholder,
                Placeholder.unparsed("status", TicketStatusLabel.of(result.change.ticket)),
            )
            TicketStatusChangeResult.TicketNotFound -> player.sendRichMessage("<red>お問い合わせが見つかりません。")
            TicketStatusChangeResult.NotPermitted -> player.sendRichMessage("<red>この操作を行う権限がありません。")
            TicketStatusChangeResult.AlreadyClosed ->
                player.sendRichMessage("<yellow>お問い合わせ #<id> は既にクローズされています。", idPlaceholder)
            TicketStatusChangeResult.AlreadyOpen ->
                player.sendRichMessage("<yellow>お問い合わせ #<id> は既にオープンしています。", idPlaceholder)
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

    companion object {
        /** チケット id の Tab 補完に使うサジェストプロバイダーの名前 */
        const val TICKET_ID_SUGGESTIONS: String = "ticket-ids"

        /** オープン中のチケット id の Tab 補完に使うサジェストプロバイダーの名前 */
        const val OPEN_TICKET_ID_SUGGESTIONS: String = "open-ticket-ids"

        /** クローズされたチケット id の Tab 補完に使うサジェストプロバイダーの名前 */
        const val CLOSED_TICKET_ID_SUGGESTIONS: String = "closed-ticket-ids"

        /** クローズする理由の Tab 補完に使うサジェストプロバイダーの名前 */
        const val CLOSE_REASON_SUGGESTIONS: String = "ticket-close-reasons"

        /** オンラインのプレイヤー名の Tab 補完に使うサジェストプロバイダーの名前 */
        const val ONLINE_PLAYER_SUGGESTIONS: String = "ticket-online-players"

        /** ブロック座標からブロックの中央へずらす量 */
        private const val BLOCK_CENTER: Double = 0.5

        /** Tab 補完に出すチケット id の最大件数 (多すぎると候補の一覧が読めなくなる) */
        private const val MAX_SUGGESTIONS: Int = 30
    }
}
