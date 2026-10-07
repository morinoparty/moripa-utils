/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.velocity.reload.command

import com.velocitypowered.api.command.CommandSource
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.velocity.VelocityCommandManager

/**
 * 設定の再読み込みコマンドを Cloud のコマンドマネージャーへ登録する
 */
object ReloadCommandRegistrar {
    /**
     * /muv reload コマンドを登録する
     *
     * @param manager プロキシの初期化時に生成したコマンドマネージャー
     */
    fun register(manager: VelocityCommandManager<CommandSource>) {
        // Velocity 版は kotlinx-coroutines を同梱しないため、コルーチン対応 (installCoroutineSupport) は使わない
        AnnotationParser(manager, CommandSource::class.java)
            .parse(ReloadCommand())
    }
}
