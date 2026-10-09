/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.schematic.command

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.paper.PaperCommandManager

/**
 * schematic 機能のコマンドを Cloud のコマンドマネージャーへ登録する
 */
object SchematicCommandRegistrar {
    /**
     * /mu schematic upload コマンドを登録する
     *
     * WorldEdit の有無やストレージの設定は再読み込みや他プラグインの導入で変わるため、登録は常に行い、
     * 使えるかどうかは実行時に判定する。
     * Cloud は最初の COMMANDS ライフサイクルイベントでコマンドの登録を締め切るため、
     * [party.morino.moripautils.paper.MoripaUtilsBootstrap] から呼ぶこと。
     *
     * @param manager ブートストラップ段階で生成したコマンドマネージャー
     */
    fun register(manager: PaperCommandManager<CommandSourceStack>) {
        AnnotationParser(manager, CommandSourceStack::class.java)
            // suspend fun のコマンドハンドラーを使えるようにする
            .installCoroutineSupport()
            .parse(SchematicCommand())
    }
}
