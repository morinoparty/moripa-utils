/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.velocity.reload.command

import com.velocitypowered.api.command.CommandSource
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.reload.ReloadResult
import party.morino.moripautils.common.reload.ReloadPermissions
import party.morino.moripautils.velocity.MoripaUtils

/**
 * /muv reload コマンド (config.conf の再読み込み)
 *
 * 再読み込みのたびに Koin コンテナが作り直されるため、プラグインはコマンドの実行時にコンテナから取り出す。
 * Velocity 版の JAR は kotlinx-coroutines を同梱しないため、ハンドラーは suspend にしない
 * (コマンドマネージャーの非同期コーディネーターにより、プロキシのイベントスレッド外で実行される)。
 */
class ReloadCommand {
    /** Velocity の CommandSource には sendRichMessage が無いため、MiniMessage で変換してから送る */
    private val miniMessage = MiniMessage.miniMessage()

    /**
     * config.conf を読み込み直して各機能を起動し直す
     *
     * @param source コマンドの実行元 (コンソールからも実行できる)
     */
    @Command("muv reload")
    @Permission(ReloadPermissions.RELOAD)
    @CommandDescription("MoripaUtils (Velocity) の設定を再読み込みします")
    fun reload(source: CommandSource) {
        val plugin = MoripaUtilsKoinContext.getOrNull()?.getOrNull<MoripaUtils>()
        if (plugin == null) {
            source.sendMessage(miniMessage.deserialize("<red>MoripaUtils が有効になっていないため再読み込みできません。"))
            return
        }

        val message = when (val result = plugin.reload()) {
            is ReloadResult.Success -> miniMessage.deserialize("<green>MoripaUtils の設定を再読み込みしました。")
            // エラーメッセージに < が含まれてもタグとして解釈されないよう、unparsed で埋め込む
            is ReloadResult.InvalidConfig -> miniMessage.deserialize(
                "<red>config.conf の読み込みに失敗したため、以前の設定のまま動作しています: <error>",
                Placeholder.unparsed("error", result.message),
            )
            ReloadResult.InProgress -> miniMessage.deserialize("<yellow>別の再読み込みを実行中です。")
        }
        source.sendMessage(message)
    }
}
