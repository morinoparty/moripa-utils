/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper

import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import org.bukkit.plugin.java.JavaPlugin
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.paper.PaperCommandManager
import party.morino.moripautils.common.config.MoripaUtilsConfigLoader
import party.morino.moripautils.common.model.config.MoripaUtilsConfig
import party.morino.moripautils.paper.reload.command.ReloadCommandRegistrar
import party.morino.moripautils.paper.ticket.command.TicketCommandRegistrar
import java.io.IOException

/**
 * Paper のブートストラッパー
 *
 * Cloud のコマンドマネージャーはブートストラップ段階で生成する必要があるため、ここで生成してプラグイン本体へ渡す。
 * Cloud はブートストラップで登録したハンドラーの COMMANDS イベント (プラグインの有効化より前に発火する) で
 * コマンドの登録を締め切るため、各機能のコマンドもここで登録する。
 * どの機能を有効にするかは config.conf で決まるため、ここでも設定を読み込む。
 */
@Suppress("unused", "UnstableApiUsage")
class MoripaUtilsBootstrap : PluginBootstrap {

    /** ブートストラップ段階で生成したコマンドマネージャー (createPlugin でプラグイン本体へ渡す) */
    private var commandManager: PaperCommandManager<CommandSourceStack>? = null

    /** /ticket を登録したかどうか (再読み込みで ticket 機能を有効にした場合に再起動が必要か判定するためプラグイン本体へ渡す) */
    private var ticketCommandRegistered: Boolean = false

    override fun bootstrap(context: BootstrapContext) {
        val manager =
            PaperCommandManager
                .builder()
                .executionCoordinator(ExecutionCoordinator.asyncCoordinator())
                .buildBootstrapped(context)
        commandManager = manager
        // /mu reload はどの機能にも属さないため、config.conf の内容に関係なく登録する
        ReloadCommandRegistrar.register(manager)

        val config = loadConfigOrNull(context) ?: return
        // 無効な機能のコマンドは登録しない
        if (config.ticket.enabled) {
            TicketCommandRegistrar.register(manager)
            ticketCommandRegistered = true
        }
    }

    /**
     * コマンドの登録可否を決めるために config.conf を読み込む
     *
     * 設定が壊れている場合はコマンドを登録せずに続行する (エラーの詳細はプラグインの有効化時に改めて出し、そこで起動を止める)。
     *
     * @param context ブートストラップのコンテキスト (データフォルダとロガーを使う)
     * @return 読み込んだ設定、読み込めなかった場合は null
     */
    private fun loadConfigOrNull(context: BootstrapContext): MoripaUtilsConfig? = try {
        MoripaUtilsConfigLoader(context.dataDirectory, MoripaUtilsConfig()).load()
    } catch (e: IllegalStateException) {
        // 構文や値の誤り
        context.logger.warn("Skipping command registration because config.conf is invalid: {}", e.message)
        null
    } catch (e: IOException) {
        // データフォルダの作成や読み書きの失敗
        context.logger.warn("Skipping command registration because config.conf could not be read: {}", e.message)
        null
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        return MoripaUtils(commandManager, ticketCommandRegistered)
    }
}
