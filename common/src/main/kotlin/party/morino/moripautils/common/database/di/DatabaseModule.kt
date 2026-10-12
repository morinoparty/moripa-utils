/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.database.di

import org.koin.core.module.Module
import org.koin.dsl.module
import party.morino.moripautils.common.database.MoripaUtilsDatabase
import party.morino.moripautils.common.model.config.DatabaseConfig
import java.nio.file.Path

/**
 * 共有データベースの Koin モジュールを生成するファクトリ
 *
 * データベースを使う機能 (ticket など) が有効な場合にだけ読み込む (Velocity では MySQL の場合のみ)。
 */
object DatabaseModule {
    /**
     * 共有データベースのモジュールを生成する
     *
     * @param dataDirectory プラグインのデータフォルダ (SQLite ファイルの基準になる)
     * @param config データベースの設定
     * @return [MoripaUtilsDatabase] をシングルトンとして提供する Koin モジュール
     */
    fun create(dataDirectory: Path, config: DatabaseConfig): Module = module {
        single { MoripaUtilsDatabase(config, dataDirectory) }
    }
}
