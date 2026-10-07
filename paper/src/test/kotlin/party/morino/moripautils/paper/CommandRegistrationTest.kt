/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.incendo.cloud.CommandManager
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.internal.CommandRegistrationHandler
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import party.morino.moripautils.paper.reload.command.ReloadCommand
import party.morino.moripautils.paper.ticket.command.TicketCommand

/**
 * コマンドクラスを Cloud のアノテーションパーサーに通せることを検証する
 *
 * 補完メソッドのシグネチャ誤りなどはブートストラップ時に初めて例外になり、プラグイン自体が読み込まれなくなるため、
 * 実サーバーを起動しなくても気付けるようにする。
 */
class CommandRegistrationTest {
    /** コマンドを実際には登録しない、パース確認用のコマンドマネージャー */
    private class TestCommandManager :
        CommandManager<CommandSourceStack>(
            ExecutionCoordinator.simpleCoordinator(),
            CommandRegistrationHandler.nullCommandRegistrationHandler(),
        ) {
        override fun hasPermission(sender: CommandSourceStack, permission: String): Boolean = true
    }

    @Test
    @DisplayName("Ticket and reload commands are accepted by the annotation parser")
    fun commandsCanBeParsed() {
        // 本番の登録処理 (TicketCommandRegistrar / ReloadCommandRegistrar) と同じ設定でパースする
        val parser = AnnotationParser(TestCommandManager(), CommandSourceStack::class.java).installCoroutineSupport()

        assertDoesNotThrow { parser.parse(TicketCommand()) }
        assertDoesNotThrow { parser.parse(ReloadCommand()) }
    }
}
