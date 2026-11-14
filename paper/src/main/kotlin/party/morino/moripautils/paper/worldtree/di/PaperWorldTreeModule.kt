/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.worldtree.di

import org.koin.core.module.Module
import org.koin.dsl.module
import party.morino.moripautils.paper.worldtree.WorldTreeService

/**
 * world tree 機能の Koin モジュールを生成するファクトリ
 */
object PaperWorldTreeModule {
    /**
     * world tree モジュールを生成する
     *
     * @return 世界樹の季節を切り替えるサービスをシングルトンとして提供する Koin モジュール
     */
    fun create(): Module = module {
        // 設定は登録したコンテナのものを固定で渡す
        single { WorldTreeService(get()) }
    }
}
