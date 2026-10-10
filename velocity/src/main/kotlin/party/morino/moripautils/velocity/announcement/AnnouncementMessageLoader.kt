/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.velocity.announcement

import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.koin.core.component.inject
import org.slf4j.Logger
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.velocity.model.announcement.AnnouncementMessage
import party.morino.moripautils.velocity.model.announcement.AnnouncementMessageFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * message ディレクトリからお知らせのメッセージファイル (<title>.json) を読み込む
 *
 * @param directory メッセージファイルを置くディレクトリ (plugins/moripa-utils/message)
 */
class AnnouncementMessageLoader(
    private val directory: Path,
) : MoripaUtilsKoinComponent {
    private val logger: Logger by inject()

    /**
     * メッセージファイルをファイル名の順にすべて読み込む
     *
     * ディレクトリがなければ作成する (初回起動時に置き場所が分かるようにするため)。
     * 読み込めないファイルは警告を出して飛ばし、残りのファイルでお知らせを続ける。
     *
     * @return ファイル名の順に並んだお知らせ
     * @throws IOException ディレクトリの作成や一覧の取得に失敗した場合
     */
    fun load(): List<AnnouncementMessage> {
        Files.createDirectories(directory)
        return directory
            .listDirectoryEntries(GLOB)
            // *.json という名前のディレクトリは飛ばす
            .filter { it.isRegularFile() }
            // 送る順番をファイル名で決められるよう、名前の順に並べる
            .sortedBy { it.fileName.toString() }
            .mapNotNull { file -> loadOrNull(file) }
    }

    /**
     * 1 つのメッセージファイルを読み込む
     *
     * @param file メッセージファイル
     * @return 読み込んだお知らせ。読み込みやデコードに失敗した場合は null
     */
    private fun loadOrNull(file: Path): AnnouncementMessage? {
        val messageFile = try {
            json.decodeFromString(AnnouncementMessageFile.serializer(), file.readText())
        } catch (e: IOException) {
            logger.warn("Skipped announcement message {} because it could not be read: {}", file.fileName, e.message)
            return null
        } catch (e: IllegalArgumentException) {
            // SerializationException と init ブロックの require 失敗は IllegalArgumentException のサブクラスなので、ここでまとめて捕捉する
            logger.warn("Skipped announcement message {} because it is invalid: {}", file.fileName, e.message)
            return null
        }
        return AnnouncementMessage(file.nameWithoutExtension, render(messageFile.lines))
    }

    /**
     * 行を MiniMessage として変換し、改行でつないで 1 つのメッセージにする
     *
     * @param lines MiniMessage 形式の行
     * @return チャットに送る内容
     */
    private fun render(lines: List<String>): Component =
        Component.join(JoinConfiguration.newlines(), lines.map { miniMessage.deserialize(it) })

    private companion object {
        /** メッセージファイルとして読み込むファイル名のパターン */
        const val GLOB: String = "*.json"

        /** メッセージファイルの読み込みに使う JSON 設定 (書き間違えたキーに気付けるよう、未知のキーはエラーにする) */
        val json: Json = Json

        /** 行の変換に使う MiniMessage */
        val miniMessage: MiniMessage = MiniMessage.miniMessage()
    }
}
