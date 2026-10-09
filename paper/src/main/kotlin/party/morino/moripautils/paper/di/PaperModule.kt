/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.di

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Server
import org.incendo.cloud.paper.PaperCommandManager
import org.koin.core.module.Module
import org.koin.dsl.module
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.observability.metrics.MetricsSampler
import party.morino.moripautils.paper.schematic.SchematicUploader

/**
 * Paper 固有の Koin モジュールを生成するファクトリ
 *
 * 共通モジュール ([party.morino.moripautils.common.di.CommonModule]) と組み合わせて使う。
 */
object PaperModule {
    /**
     * Paper モジュールを生成する
     *
     * @param plugin 有効化中のプラグインインスタンス
     * @return プラグイン / Bukkit サーバー / コマンドマネージャー / サンプラー / schematic のアップロード役をシングルトンとして提供する Koin モジュール
     */
    fun create(plugin: MoripaUtils): Module = module {
        // プラグイン本体 (ロガーやコルーチンの起動に使う)
        single<MoripaUtils> { plugin }
        // コレクターが Bukkit API を読むためのサーバー
        single<Server> { plugin.server }
        // 各機能がコマンドを登録する Cloud のコマンドマネージャー (ブートストラッパー経由で起動した場合のみ存在する)
        plugin.commandManager?.let { manager ->
            single<PaperCommandManager<CommandSourceStack>> { manager }
        }
        // メインスレッド上で定期的にサンプリングを行うサンプラー
        single { MetricsSampler() }
        // schematic のアップロードの入口 (WorldEdit / ストレージの有無は実行時に判定するため常に登録する)
        single { SchematicUploader() }
    }
}
