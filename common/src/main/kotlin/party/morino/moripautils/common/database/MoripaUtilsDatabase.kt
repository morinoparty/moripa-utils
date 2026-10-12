/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.DatabaseConfig as ExposedDatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import party.morino.moripautils.common.model.config.DatabaseConfig
import party.morino.moripautils.common.model.config.DatabaseType
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection

/**
 * MoripaUtils の各機能が共有するデータベース (SQLite / MySQL) への接続
 *
 * 各機能はこのクラスの [query] を通して、同じデータベースの中の機能ごとのテーブルを読み書きする。
 * 接続は最初の [query] で I/O スレッド上に作り、メインスレッドでの I/O を避ける。
 * データベースを使う機能が有効な場合にだけ生成する。
 * Velocity の JAR には SQLite の JDBC ドライバーを同梱していないため、Velocity では MySQL の場合にだけ生成すること。
 *
 * @param config データベースの設定
 * @param dataDirectory プラグインのデータフォルダ (SQLite ファイルの基準になる)
 */
class MoripaUtilsDatabase(
    private val config: DatabaseConfig,
    private val dataDirectory: Path,
) : AutoCloseable {
    /**
     * DB 操作専用のディスパッチャー
     *
     * SQLite は同時書き込みに弱いため並列度 1 で直列に実行する。MySQL は同時接続を扱えるので少しだけ並列にする。
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(
        when (config.type) {
            DatabaseType.SQLITE -> 1
            DatabaseType.MYSQL -> MYSQL_PARALLELISM
        },
    )

    /** 接続済みのデータベース (初回アクセス時に接続する) */
    private val databaseDelegate = lazy { connect() }
    private val database: Database by databaseDelegate

    /**
     * DB 専用のディスパッチャー上でトランザクションを実行する
     *
     * 他のプラグインの Exposed 設定に影響されないよう、必ずこのクラスの接続を明示して使う。
     *
     * @param T 処理の戻り値の型
     * @param block トランザクション内で実行する処理
     * @return 処理の結果
     */
    suspend fun <T> query(block: JdbcTransaction.() -> T): T = withContext(dispatcher) {
        transaction(database) { block() }
    }

    /**
     * 接続を解放する (プラグインの無効化時に呼ぶ)
     */
    override fun close() {
        // 一度も使われていなければ接続も存在しないので何もしない
        if (databaseDelegate.isInitialized()) {
            TransactionManager.closeAndUnregister(database)
        }
    }

    /**
     * 設定された種類のデータベースに接続する
     *
     * @return 接続済みのデータベース
     */
    private fun connect(): Database {
        val url = JdbcUrlBuilder.build(config, dataDirectory)
        return when (config.type) {
            DatabaseType.SQLITE -> {
                // データベースファイルをサブディレクトリに置く設定にも対応する
                JdbcUrlBuilder.sqliteFile(config, dataDirectory).parent?.let { Files.createDirectories(it) }
                Database.connect(
                    url = url,
                    driver = SQLITE_DRIVER,
                    // SQLite は Exposed 既定の分離レベルに対応していないため SERIALIZABLE を指定する
                    databaseConfig = ExposedDatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE },
                )
            }
            DatabaseType.MYSQL -> Database.connect(
                url = url,
                driver = MYSQL_DRIVER,
                user = config.mysql.user,
                password = config.mysql.password,
            )
        }
    }

    companion object {
        /** SQLite の JDBC ドライバー (Paper 本体に同梱されている org.xerial:sqlite-jdbc) */
        private const val SQLITE_DRIVER = "org.sqlite.JDBC"

        /** MySQL の JDBC ドライバー (Paper では本体同梱、Velocity では JAR に同梱した com.mysql:mysql-connector-j) */
        private const val MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver"

        /** MySQL で同時に実行するトランザクションの上限 (接続プールを持たないため控えめにする) */
        private const val MYSQL_PARALLELISM = 4
    }
}
