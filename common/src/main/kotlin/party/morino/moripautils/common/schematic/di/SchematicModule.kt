/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.schematic.di

import org.koin.core.module.Module
import org.koin.dsl.module
import party.morino.moripautils.common.schematic.SchematicUploadService

/**
 * schematic 機能の共通 Koin モジュールを生成するファクトリ
 *
 * アップロード先の [party.morino.moripautils.common.storage.ObjectStorage] を使うため、
 * [party.morino.moripautils.common.storage.di.StorageModule] と一緒に読み込むこと。
 */
object SchematicModule {
    /**
     * schematic 機能の共通モジュールを生成する
     *
     * @return [SchematicUploadService] をシングルトンとして提供する Koin モジュール
     */
    fun create(): Module = module {
        single { SchematicUploadService() }
    }
}
