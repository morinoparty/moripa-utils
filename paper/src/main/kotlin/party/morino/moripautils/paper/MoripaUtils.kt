/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.incendo.cloud.paper.PaperCommandManager
import org.koin.core.component.get
import party.morino.moripautils.common.MoripaUtilsCommon
import party.morino.moripautils.common.config.MoripaUtilsConfigLoader
import party.morino.moripautils.common.database.MoripaUtilsDatabase
import party.morino.moripautils.common.database.di.DatabaseModule
import party.morino.moripautils.common.di.CommonModule
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.common.model.config.DatabaseConfig
import party.morino.moripautils.common.model.config.MoripaUtilsConfig
import party.morino.moripautils.common.model.config.ObservabilityConfig
import party.morino.moripautils.common.model.reload.ReloadResult
import party.morino.moripautils.common.observability.http.MetricsHttpServer
import party.morino.moripautils.common.observability.metrics.JvmMetricsCollector
import party.morino.moripautils.common.observability.metrics.MetricsCollector
import party.morino.moripautils.common.observability.metrics.MetricsExporter
import party.morino.moripautils.common.observability.metrics.SampledMetricsCollector
import party.morino.moripautils.common.ticket.TicketService
import party.morino.moripautils.common.ticket.di.TicketModule
import party.morino.moripautils.paper.di.PaperModule
import party.morino.moripautils.paper.observability.metrics.ChunkEventListener
import party.morino.moripautils.paper.observability.metrics.MetricsSampler
import party.morino.moripautils.paper.observability.metrics.PlayerEventListener
import party.morino.moripautils.paper.observability.metrics.PlayerMetricsCollector
import party.morino.moripautils.paper.observability.metrics.ServerInfoCollector
import party.morino.moripautils.paper.observability.metrics.TickDurationListener
import party.morino.moripautils.paper.observability.metrics.TickMetricsCollector
import party.morino.moripautils.paper.observability.metrics.WorldMetricsCollector
import party.morino.moripautils.paper.ticket.di.PaperTicketModule
import party.morino.moripautils.paper.ticket.mineauth.TicketMineAuthIntegration
import java.io.IOException

/**
 * MoripaUtils の Paper 向けプラグイン本体
 *
 * 有効化時に設定を読み込み、このプラグイン専用の Koin コンテナを起動して各機能を初期化する。
 * observability 機能が有効な場合は、メトリクスコレクターを登録して Prometheus 用の HTTP サーバーと
 * メインスレッドのサンプラーを起動する。
 * ticket 機能が有効な場合は、/ticket が使うサービスを読み込み、MineAuth があれば HTTP API を登録する。
 * /mu reload ([reload]) では、各機能を停止してから新しい設定で Koin コンテナごと作り直す。
 *
 * @property commandManager ブートストラップ段階で生成した Cloud のコマンドマネージャー。
 *   MockBukkit のテストなどブートストラッパーを経由せずに生成された場合は null
 * @property ticketCommandRegistered ブートストラップ段階で /ticket を登録したかどうか
 *   (再読み込みで ticket 機能を有効にしてもコマンドは増えないため、再起動が必要かの判定に使う)
 */
open class MoripaUtils(
    val commandManager: PaperCommandManager<CommandSourceStack>? = null,
    private val ticketCommandRegistered: Boolean = false,
) : SuspendingJavaPlugin(),
    MoripaUtilsKoinComponent {

    /** 再読み込みの同時実行を防ぐロック (/mu reload が連打されても 1 回ずつ処理する) */
    private val reloadMutex = Mutex()

    /**
     * 自分で登録した Bukkit のイベントリスナー
     *
     * プラグインの無効化時は Bukkit が自動で解除するが、再読み込み時は自前で解除しないと二重に登録される。
     * Cloud などがこのプラグイン名義で登録したリスナーまで消さないよう、HandlerList.unregisterAll(plugin) は使わない。
     */
    private val registeredListeners = mutableListOf<Listener>()

    /**
     * MineAuth に登録した ticket の HTTP API (未登録なら null)
     *
     * MineAuth の型を参照すると MineAuth が無い環境でクラス解決に失敗するため、AutoCloseable として保持する。
     */
    private var mineAuthRegistration: AutoCloseable? = null

    override suspend fun onEnableAsync() {
        val config = loadConfig()
        setupKoin(config)
        MoripaUtilsCommon.init()
        startFeatures(config)

        logger.info("${pluginMeta.name} v${pluginMeta.version} has been enabled!")
    }

    override suspend fun onDisableAsync() {
        stopFeatures()
        // 専用コンテナを閉じる (他プラグインの Koin には影響しない)
        MoripaUtilsKoinContext.stop()
        logger.info("${pluginMeta.name} has been disabled!")
    }

    /**
     * config.conf を読み込み直し、すべての機能を新しい設定で起動し直す (/mu reload)
     *
     * 先に新しい設定を検証し、不正な場合は稼働中の機能に触れずに失敗を返す。
     * 検証に成功した場合だけ、各機能の停止 → Koin コンテナの作り直し → 各機能の起動 をメインスレッドで行う。
     * Cloud の非同期コーディネーターなど、どのスレッドから呼んでもよい。
     *
     * @return 再読み込みの結果
     */
    suspend fun reload(): ReloadResult {
        // 実行中の再読み込みを待たせるより、重複した要求だと伝える方が分かりやすい
        if (!reloadMutex.tryLock()) {
            return ReloadResult.InProgress
        }
        try {
            // ファイルの読み込みはメインスレッドを止めないよう I/O スレッドで行う
            val config = try {
                withContext(Dispatchers.IO) { createConfigLoader().load() }
            } catch (e: IllegalStateException) {
                // 構文や値の誤り
                logger.warning("Reload aborted because ${MoripaUtilsConfigLoader.CONFIG_FILE_NAME} is invalid: ${e.message}")
                return ReloadResult.InvalidConfig(e.message ?: e.toString())
            } catch (e: IOException) {
                // ファイルの読み書きの失敗
                logger.warning("Reload aborted because ${MoripaUtilsConfigLoader.CONFIG_FILE_NAME} could not be read: ${e.message}")
                return ReloadResult.InvalidConfig(e.message ?: e.toString())
            }

            // リスナーの登録 / 解除やサンプラーの初回サンプリングはメインスレッドで行う必要がある
            withContext(minecraftDispatcher) {
                stopFeatures()
                // start() は古いコンテナを閉じてから作り直す
                setupKoin(config)
                startFeatures(config)
            }
            logger.info("Reloaded ${MoripaUtilsConfigLoader.CONFIG_FILE_NAME}")

            // /ticket はブートストラップ段階でしか登録できない
            val restartRequired = config.ticket.enabled && !ticketCommandRegistered
            if (restartRequired) {
                logger.warning("Ticket was enabled by reload, but /ticket is not registered until the server restarts")
            }
            return ReloadResult.Success(restartRequiredForCommands = restartRequired)
        } finally {
            reloadMutex.unlock()
        }
    }

    /**
     * 設定で有効化されている機能を起動する
     *
     * @param config 読み込み済みの設定
     */
    private fun startFeatures(config: MoripaUtilsConfig) {
        // 機能ごとに設定で有効化されている場合だけ起動する
        if (config.observability.enabled) {
            startObservability(config.observability)
        } else {
            logger.info("Observability is disabled in config.conf")
        }
        if (config.ticket.enabled) {
            startTicket(config.database)
        } else {
            logger.info("Ticket is disabled in config.conf")
        }
    }

    /**
     * 起動中の機能をすべて停止する (Koin コンテナ自体は閉じない)
     *
     * 起動していない機能や、有効化に失敗して Koin が存在しない場合は何もしない。
     */
    private fun stopFeatures() {
        // 有効化に失敗して Koin やモジュールが存在しない場合もあるため、定義があるときだけ停止処理を行う
        val koin = MoripaUtilsKoinContext.getOrNull()
        koin?.getOrNull<MetricsSampler>()?.stop()
        // 古いレジストリへ書き込み続けないよう、エクスポーターより先にイベントの購読をやめる
        registeredListeners.forEach { listener -> HandlerList.unregisterAll(listener) }
        registeredListeners.clear()
        koin?.getOrNull<MetricsExporter>()?.stop()
        // 古いコンテナを参照する API ハンドラーが残らないよう、MineAuth の登録を解除する
        closeMineAuthRegistration()
        // ticket 機能が無効な場合はサービスもデータベースも定義されていない
        koin?.getOrNull<TicketService>()?.close()
        koin?.getOrNull<MoripaUtilsDatabase>()?.close()
    }

    /**
     * データフォルダの config.conf を読み込む (存在しなければ既定値を書き出す)
     *
     * @return 読み込んだ設定
     * @throws IllegalStateException config.conf の内容が不正な場合 (プラグインの有効化を失敗させる)
     */
    private fun loadConfig(): MoripaUtilsConfig {
        val loader = createConfigLoader()
        return try {
            loader.load()
        } catch (e: IllegalStateException) {
            // 壊れた設定で黙って既定値を使うより、起動を止めて気付いてもらう
            logger.severe("Failed to load ${MoripaUtilsConfigLoader.CONFIG_FILE_NAME}: ${e.message}")
            throw e
        }
    }

    /**
     * データフォルダの config.conf を扱うローダーを生成する
     *
     * @return Paper 向けの既定値を持つローダー
     */
    private fun createConfigLoader(): MoripaUtilsConfigLoader = MoripaUtilsConfigLoader(dataFolder.toPath(), MoripaUtilsConfig())

    /**
     * このプラグイン専用の Koin コンテナを起動する
     *
     * 他プラグインと GlobalContext を共有しないよう、[MoripaUtilsKoinContext] に独立したコンテナを作る。
     *
     * @param config 読み込み済みの設定
     */
    private fun setupKoin(config: MoripaUtilsConfig) {
        MoripaUtilsKoinContext.start(
            listOf(
                CommonModule.create(config),
                PaperModule.create(this),
            ),
        )
    }

    /**
     * ticket 機能 (/ticket によるお問い合わせ) を起動する
     *
     * /ticket コマンド自体はブートストラップ段階で登録済み ([MoripaUtilsBootstrap])。ここではコマンドが使う
     * サービスなどを Koin に読み込み、MineAuth があれば HTTP API を登録する。
     * チケットは共有データベースの tickets テーブルに保存し、接続は最初のチケット操作時に I/O スレッドで行う。
     *
     * @param databaseConfig 共有データベースの設定
     */
    private fun startTicket(databaseConfig: DatabaseConfig) {
        MoripaUtilsKoinContext.loadModules(
            listOf(
                DatabaseModule.create(dataFolder.toPath(), databaseConfig),
                TicketModule.create(logger),
                PaperTicketModule.create(),
            ),
        )
        // MineAuth は任意依存。API クラスに触れる前に Bukkit の API だけで存在を確認する
        if (server.pluginManager.getPlugin(MINEAUTH_PLUGIN_NAME) != null) {
            mineAuthRegistration = registerMineAuthSafely()
        } else {
            logger.info("MineAuth is not installed; ticket HTTP endpoints are disabled")
        }
    }

    /**
     * MineAuth 連携を登録する (失敗してもプラグイン全体は止めない)
     *
     * 導入済みの MineAuth が compileOnly の mineauth-api と互換でない場合、クラス解決時に
     * LinkageError (NoClassDefFoundError / NoSuchMethodError など) が発生する。
     * これが onEnableAsync の外へ漏れると、起動済みの observability ごとプラグインが無効化されるため、ここで握りつぶす。
     *
     * @return 登録の解除に使うハンドル。登録できなかった場合は null
     */
    @Suppress("TooGenericExceptionCaught")
    private fun registerMineAuthSafely(): AutoCloseable? {
        return try {
            TicketMineAuthIntegration().register()
        } catch (e: CancellationException) {
            // コルーチンのキャンセルは握りつぶさず伝播させる
            throw e
        } catch (e: LinkageError) {
            // API の互換性が無い MineAuth が導入されている
            logger.warning("MineAuth integration failed (incompatible MineAuth API?); ticket HTTP endpoints are disabled: $e")
            null
        } catch (e: Exception) {
            // その他の予期しない失敗も ticket の HTTP API だけを諦める
            logger.warning("MineAuth integration failed; ticket HTTP endpoints are disabled: $e")
            null
        }
    }

    /**
     * MineAuth に登録した ticket の HTTP API を解除する
     *
     * 登録していない場合は何もしない。解除に失敗しても他の機能の停止は続けたいため、例外はログに残すだけにする。
     */
    @Suppress("TooGenericExceptionCaught")
    private fun closeMineAuthRegistration() {
        val registration = mineAuthRegistration ?: return
        mineAuthRegistration = null
        try {
            registration.close()
        } catch (e: Exception) {
            logger.warning("Failed to unregister ticket HTTP endpoints from MineAuth: $e")
        }
    }

    /**
     * observability 機能 (Prometheus メトリクスの公開) を起動する
     *
     * @param config observability 機能の設定
     */
    private fun startObservability(config: ObservabilityConfig) {
        val collectors = createCollectors(config)
        // Bukkit のイベントを購読するコレクターはリスナーとして登録する
        collectors.filterIsInstance<Listener>().forEach { listener ->
            server.pluginManager.registerEvents(listener, this)
            // 再読み込み時に解除できるよう覚えておく
            registeredListeners.add(listener)
        }

        startExporter(config, collectors)
        // メインスレッドでしか読めない値は定期的にサンプリングする
        get<MetricsSampler>().start(collectors.filterIsInstance<SampledMetricsCollector>())
    }

    /**
     * 登録するコレクターの一覧を組み立てる
     *
     * @param config observability 機能の設定 (JVM メトリクスの有効 / 無効に使う)
     * @return 登録順に並んだコレクターの一覧
     */
    private fun createCollectors(config: ObservabilityConfig): List<MetricsCollector> = buildList {
        // JVM メトリクスは設定で無効化できる
        if (config.metrics.jvm) {
            add(JvmMetricsCollector())
        }
        add(ServerInfoCollector())
        add(PlayerMetricsCollector())
        add(TickMetricsCollector())
        add(TickDurationListener())
        add(WorldMetricsCollector())
        add(PlayerEventListener())
        add(ChunkEventListener())
    }

    /**
     * コレクターを登録して HTTP サーバーを起動する
     *
     * ポートのバインドに失敗しても、他のプラグインに影響しないようプラグイン自体は無効化しない。
     *
     * @param config observability 機能の設定 (ログ出力用)
     * @param collectors 登録するコレクターの一覧
     */
    private fun startExporter(config: ObservabilityConfig, collectors: List<MetricsCollector>) {
        val httpConfig = config.http
        try {
            get<MetricsExporter>().start(collectors)
        } catch (e: IOException) {
            logger.severe(
                "Failed to bind metrics HTTP server on ${httpConfig.host}:${httpConfig.port} - is the port in use? (${e.message})",
            )
            return
        }
        // ポートに 0 を指定した場合は自動選択された実際のポートを表示する
        val port = get<MetricsHttpServer>().port ?: httpConfig.port
        logger.info("Metrics are available at http://${httpConfig.host}:$port${httpConfig.path}")
    }

    companion object {
        /** 連携する MineAuth のプラグイン名 */
        private const val MINEAUTH_PLUGIN_NAME = "MineAuth"
    }
}
