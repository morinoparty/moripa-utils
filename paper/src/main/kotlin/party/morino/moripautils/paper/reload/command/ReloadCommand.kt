/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.reload.command

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.reload.ReloadResult
import party.morino.moripautils.common.reload.ReloadPermissions
import party.morino.moripautils.paper.MoripaUtils

/**
 * /mu reload コマンド (config.conf の再読み込み)
 *
 * ブートストラップ段階で登録するため、生成時点では Koin コンテナがまだ存在しない。
 * また再読み込みのたびにコンテナが作り直されるため、プラグインはコマンドの実行時にコンテナから取り出す。
 */
@Suppress("UnstableApiUsage")
class ReloadCommand {
    /**
     * config.conf を読み込み直して各機能を起動し直す
     *
     * @param source コマンドの実行元 (コンソールからも実行できる)
     */
    @Command("mu reload")
    @Permission(ReloadPermissions.RELOAD)
    @CommandDescription("MoripaUtils の設定を再読み込みします")
    suspend fun reload(source: CommandSourceStack) {
        val sender = source.sender
        // プラグインの有効化に失敗した場合はコンテナが存在しない (設定を直したらサーバーの再起動が必要)
        val plugin = MoripaUtilsKoinContext.getOrNull()?.getOrNull<MoripaUtils>()
        if (plugin == null) {
            sender.sendRichMessage("<red>MoripaUtils が有効になっていないため再読み込みできません。")
            return
        }

        when (val result = plugin.reload()) {
            is ReloadResult.Success -> {
                sender.sendRichMessage("<green>MoripaUtils の設定を再読み込みしました。")
                if (result.restartRequiredForCommands) {
                    sender.sendRichMessage("<yellow>/ticket コマンドを使えるようにするにはサーバーの再起動が必要です。")
                }
            }
            // エラーメッセージに < が含まれてもタグとして解釈されないよう、unparsed で埋め込む
            is ReloadResult.InvalidConfig -> sender.sendRichMessage(
                "<red>config.conf の読み込みに失敗したため、以前の設定のまま動作しています: <error>",
                Placeholder.unparsed("error", result.message),
            )
            ReloadResult.InProgress -> sender.sendRichMessage("<yellow>別の再読み込みを実行中です。")
        }
    }
}
