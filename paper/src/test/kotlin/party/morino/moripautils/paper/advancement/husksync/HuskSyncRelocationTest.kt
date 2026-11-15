/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.advancement.husksync

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readBytes

/**
 * HuskSync が JAR 内で別のパッケージへ移しているライブラリの型を参照していないことを確かめる
 *
 * 参照していると、コンパイルは通っても実行時に NoClassDefFoundError になる。
 * 対象のパッケージは HuskSync 4.1.0 の bukkit/build.gradle の relocate に合わせている。
 */
class HuskSyncRelocationTest {
    @Test
    @DisplayName("The HuskSync gateway does not reference libraries relocated inside the HuskSync jar")
    fun gatewayDoesNotReferenceRelocatedLibraries() {
        val anchor = HuskSyncAdvancementGateway::class.java
        val directory = Path.of(anchor.protectionDomain.codeSource.location.toURI())
            .resolve(anchor.packageName.replace('.', '/'))
        val classes = Files.list(directory).use { paths -> paths.filter { it.extension == "class" }.toList() }
        assertTrue(classes.isNotEmpty(), "No compiled classes found in $directory")

        val violations = classes.flatMap { path ->
            // 定数プールのクラス名やメソッドの記述子は、内部名 (a/b/C) の文字列として含まれる
            val content = String(path.readBytes(), Charsets.ISO_8859_1)
            RELOCATED_PACKAGES.filter { content.contains(it) }.map { "${path.name} -> $it" }
        }
        assertTrue(violations.isEmpty(), "Relocated HuskSync libraries are referenced: $violations")
    }

    companion object {
        /** HuskSync が net.william278.husksync.libraries の下へ移しているパッケージ (内部名) */
        private val RELOCATED_PACKAGES = listOf(
            "org/apache/commons/",
            "com/google/gson/",
            "com/fatboyindustrial/",
            "com/zaxxer/",
            "de/exlll/",
            "net/william278/uniform/",
            "net/william278/toilet/",
            "net/william278/desertwell/",
            "net/william278/mapdataapi/",
            "org/json/",
            "net/querz/",
            "de/tr7zw/changeme/nbtapi/",
        )
    }
}
