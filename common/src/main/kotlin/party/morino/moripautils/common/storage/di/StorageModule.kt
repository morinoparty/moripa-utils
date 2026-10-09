/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.storage.di

import org.koin.core.module.Module
import org.koin.dsl.module
import party.morino.moripautils.common.model.config.StorageConfig
import party.morino.moripautils.common.storage.ObjectStorage
import party.morino.moripautils.common.storage.S3ObjectStorage

/**
 * 共有オブジェクトストレージの Koin モジュールを生成するファクトリ
 *
 * AWS SDK は Paper の PluginLoader が実行時に解決するため、Paper でストレージが設定されている場合にだけ読み込むこと。
 */
object StorageModule {
    /**
     * 共有オブジェクトストレージのモジュールを生成する
     *
     * @param config ストレージの設定 ([StorageConfig.isConfigured] が true であること)
     * @return [ObjectStorage] をシングルトンとして提供する Koin モジュール
     */
    fun create(config: StorageConfig): Module = module {
        single<ObjectStorage> { S3ObjectStorage(config) }
    }
}
