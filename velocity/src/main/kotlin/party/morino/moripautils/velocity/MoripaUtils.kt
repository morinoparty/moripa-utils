/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.velocity

import com.google.inject.Inject
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.velocity.VelocityCommandManager
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import org.koin.core.component.get
import org.slf4j.Logger
import party.morino.moripautils.common.BuildConstants
import party.morino.moripautils.common.MoripaUtilsCommon
import party.morino.moripautils.common.classloader.PluginJarPin
import party.morino.moripautils.common.config.MoripaUtilsConfigLoader
import party.morino.moripautils.common.di.CommonModule
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.observability.http.MetricsHttpServer
import party.morino.moripautils.common.observability.metrics.JvmMetricsCollector
import party.morino.moripautils.common.observability.metrics.MetricsCollector
import party.morino.moripautils.common.observability.metrics.MetricsExporter
import party.morino.moripautils.common.model.config.HttpServerConfig
import party.morino.moripautils.common.model.config.MoripaUtilsConfig
import party.morino.moripautils.common.model.config.ObservabilityConfig
import party.morino.moripautils.common.model.reload.ReloadResult
import party.morino.moripautils.velocity.di.VelocityModule
import party.morino.moripautils.velocity.reload.command.ReloadCommandRegistrar
import party.morino.moripautils.velocity.observability.metrics.ConnectionEventListener
import party.morino.moripautils.velocity.observability.metrics.ProxyInfoCollector
import party.morino.moripautils.velocity.observability.metrics.ProxyPlayerMetricsCollector
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MoripaUtils の Velocity 向けプラグイン本体 (現在は observability 機能としてプロキシのメトリクスを Prometheus 形式で公開する)
 *
 * コンストラクタ引数は Velocity (Guice) が注入する。ここ以外ではコンストラクタインジェクションを使わず、
 * 自前のクラスは Koin の `by inject()` / `get()` で依存を取得する。
 */
@Plugin(
    id = "moripa-utils",
    name = "MoripaUtils",
    // バージョンは Gradle が生成する BuildConstants から取得する (gradle.properties の version と連動)
    version = BuildConstants.VERSION,
    description = "morinoparty utility plugin (observability: proxy metrics for Prometheus / Grafana)",
    authors = ["morinoparty"],
)
class MoripaUtils @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    // config.conf を配置するプラグイン専用のデータフォルダ (plugins/moripa-utils)
    @DataDirectory private val dataDirectory: Path,
) : MoripaUtilsKoinComponent {

    /**
     * 再読み込みを実行中かどうか (/muv reload が連打されても 1 回ずつ処理する)
     *
     * Velocity 版の JAR は kotlinx-coroutines を同梱しないため、Mutex ではなく AtomicBoolean で排他する。
     */
    private val reloading = AtomicBoolean(false)

    /** 登録中の接続イベントのリスナー (再読み込み時に解除するため保持する。observability が無効なら null) */
    private var connectionEventListener: ConnectionEventListener? = null

    /**
     * プラグインの JAR を開いてキャッシュに載せる ([PluginJarPin])
     *
     * 失敗してもプラグインは動作するため、警告を出して続行する。
     */
    private fun pinPluginJar() {
        try {
            PluginJarPin.pin(javaClass)
        } catch (e: IOException) {
            logger.warn("Failed to pin the plugin jar; replacing it while running may break the plugin: {}", e.message)
        }
    }

    @Subscribe
    @Suppress("UnusedParameter")
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        // 稼働中に JAR を削除・置き換えされてもリソースを読めるよう、最初に JAR を開いておく
        pinPluginJar()
        val config = createConfigLoader().load()
        setupKoin(config)
        MoripaUtilsCommon.init()

        val commandManager = VelocityCommandManager<CommandSource>(
            server.pluginManager.ensurePluginContainer(this),
            server,
            ExecutionCoordinator.asyncCoordinator(),
            SenderMapper.identity(),
        )
        // 各機能のコマンドを登録する
        ReloadCommandRegistrar.register(commandManager)

        startFeatures(config)

        logger.info("MoripaUtils has been enabled!")
    }

    @Subscribe
    @Suppress("UnusedParameter")
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        stopFeatures()
        // 専用コンテナを閉じる (他プラグインの Koin には影響しない)
        MoripaUtilsKoinContext.stop()
        logger.info("MoripaUtils has been disabled!")
    }

    /**
     * config.conf を読み込み直し、すべての機能を新しい設定で起動し直す (/muv reload)
     *
     * 先に新しい設定を検証し、不正な場合は稼働中の機能に触れずに失敗を返す。
     * 検証に成功した場合だけ、各機能の停止 → Koin コンテナの作り直し → 各機能の起動 を行う。
     * ファイルの読み込みを含むため、プロキシのイベントスレッドではなく Cloud の非同期コーディネーターなどから呼ぶこと。
     *
     * @return 再読み込みの結果
     */
    fun reload(): ReloadResult {
        // 実行中の再読み込みを待たせるより、重複した要求だと伝える方が分かりやすい
        if (!reloading.compareAndSet(false, true)) {
            return ReloadResult.InProgress
        }
        try {
            val config = try {
                createConfigLoader().load()
            } catch (e: IllegalStateException) {
                // 構文や値の誤り
                logger.warn("Reload aborted because {} is invalid: {}", MoripaUtilsConfigLoader.CONFIG_FILE_NAME, e.message)
                return ReloadResult.InvalidConfig(e.message ?: e.toString())
            } catch (e: IOException) {
                // ファイルの読み書きの失敗
                logger.warn("Reload aborted because {} could not be read: {}", MoripaUtilsConfigLoader.CONFIG_FILE_NAME, e.message)
                return ReloadResult.InvalidConfig(e.message ?: e.toString())
            }

            stopFeatures()
            // start() は古いコンテナを閉じてから作り直す
            setupKoin(config)
            startFeatures(config)
            logger.info("Reloaded {}", MoripaUtilsConfigLoader.CONFIG_FILE_NAME)
            // Velocity のコマンドは設定に依存しないため、再起動が必要になることはない
            return ReloadResult.Success()
        } finally {
            reloading.set(false)
        }
    }

    /**
     * 設定で有効化されている機能を起動する
     *
     * チケット機能は Paper 専用のため、Velocity では observability 機能だけを扱う。
     *
     * @param config 読み込み済みの設定
     */
    private fun startFeatures(config: MoripaUtilsConfig) {
        if (config.observability.enabled) {
            startMetricsExporter(config.observability)
        } else {
            logger.info("Observability is disabled in config.conf")
        }
    }

    /**
     * 起動中の機能をすべて停止する (Koin コンテナ自体は閉じない)
     *
     * 設定の読み込みに失敗して Koin が起動していない場合などは何もしない。
     */
    private fun stopFeatures() {
        // 古いレジストリへ書き込み続けないよう、プラグイン本体の @Subscribe は残してリスナーだけを解除する
        connectionEventListener?.let { listener -> server.eventManager.unregisterListener(this, listener) }
        connectionEventListener = null
        MoripaUtilsKoinContext.getOrNull()?.getOrNull<MetricsExporter>()?.stop()
    }

    /**
     * データフォルダの config.conf を扱うローダーを生成する
     *
     * @return Velocity 向けの既定値を持つローダー
     */
    private fun createConfigLoader(): MoripaUtilsConfigLoader = MoripaUtilsConfigLoader(dataDirectory, DEFAULT_CONFIG)

    /**
     * コレクターを組み立ててイベントリスナーを登録し、メトリクスの HTTP サーバーを起動する
     *
     * ポートのバインドに失敗してもプロキシ自体の動作には影響しないため、エラーログを出すだけで続行する。
     *
     * @param config observability 機能の設定
     */
    private fun startMetricsExporter(config: ObservabilityConfig) {
        val listener = ConnectionEventListener()
        val collectors = buildList<MetricsCollector> {
            // JVM メトリクスは設定で無効化できる
            if (config.metrics.jvm) {
                add(JvmMetricsCollector())
            }
            add(ProxyPlayerMetricsCollector())
            add(ProxyInfoCollector())
            add(listener)
        }

        // @Subscribe を持つコレクターは Velocity のイベントリスナーとしても登録する (再読み込み時に解除できるよう覚えておく)
        server.eventManager.register(this, listener)
        connectionEventListener = listener

        try {
            get<MetricsExporter>().start(collectors)
        } catch (e: IOException) {
            logger.error(
                "Failed to bind metrics HTTP server on ${config.http.host}:${config.http.port} - is the port in use?",
                e,
            )
            return
        }

        // ポートに 0 を指定した場合に備えて、実際にバインドされたポートを表示する
        val port = get<MetricsHttpServer>().port ?: config.http.port
        logger.info("Metrics are exposed at http://${config.http.host}:$port${config.http.path}")
    }

    /**
     * このプラグイン専用の Koin コンテナを起動する
     *
     * 他プラグインと GlobalContext を共有しないよう、[MoripaUtilsKoinContext] に独立したコンテナを作る。
     *
     * @param config 読み込み済みの設定
     */
    private fun setupKoin(config: MoripaUtilsConfig) {
        val modules = listOf(
            CommonModule.create(config),
            VelocityModule.create(this, server, logger),
        )
        MoripaUtilsKoinContext.start(modules)
    }

    companion object {
        /**
         * Velocity 向けの既定設定
         *
         * Paper (9225) と同じホストで動かしても衝突しないよう既定ポートは 9226 にし、server も proxy にする。
         */
        private val DEFAULT_CONFIG = MoripaUtilsConfig(
            server = "proxy",
            observability = ObservabilityConfig(http = HttpServerConfig(port = 9226)),
        )
    }
}
