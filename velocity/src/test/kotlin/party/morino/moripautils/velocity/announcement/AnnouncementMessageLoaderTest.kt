/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.velocity.announcement

import io.mockk.mockk
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.module
import org.slf4j.Logger
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import java.nio.file.Files
import java.nio.file.Path

class AnnouncementMessageLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        // 読み込めないファイルの警告はモックのロガーへ流す
        MoripaUtilsKoinContext.start(listOf(module { single<Logger> { mockk(relaxed = true) } }))
    }

    @AfterEach
    fun tearDown() {
        MoripaUtilsKoinContext.stop()
    }

    @Test
    @DisplayName("Loads message files in file name order")
    fun loadsInFileNameOrder() {
        val directory = tempDir.resolve("message")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("vote.json"), """{"${'$'}schema": "https://utils.plugin.morino.party/schemas/announcement-message.json", "message_text": ["<bold>投票", "<gray>JMS"]}""")
        Files.writeString(directory.resolve("discord.json"), """{"message_text": ["Discord"]}""")
        // json 以外のファイルは読み込まない
        Files.writeString(directory.resolve("memo.txt"), "memo")

        val messages = AnnouncementMessageLoader(directory).load()

        assertEquals(listOf("discord", "vote"), messages.map { it.title })
        // 行は MiniMessage を変換したうえで改行でつながる
        val plain = PlainTextComponentSerializer.plainText().serialize(messages[1].content)
        assertEquals("投票\nJMS", plain)
    }

    @Test
    @DisplayName("Skips invalid message files")
    fun skipsInvalidFiles() {
        val directory = tempDir.resolve("message")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("broken.json"), """{"message_text": [""")
        Files.writeString(directory.resolve("empty.json"), """{"message_text": []}""")
        Files.writeString(directory.resolve("typo.json"), """{"message-text": ["typo"]}""")
        Files.writeString(directory.resolve("valid.json"), """{"message_text": ["ok"]}""")

        val messages = AnnouncementMessageLoader(directory).load()

        assertEquals(listOf("valid"), messages.map { it.title })
    }

    @Test
    @DisplayName("Creates the directory when it does not exist")
    fun createsDirectory() {
        val directory = tempDir.resolve("message")

        val messages = AnnouncementMessageLoader(directory).load()

        assertTrue(messages.isEmpty())
        assertTrue(Files.isDirectory(directory))
    }
}
