/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.classloader

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class PluginJarPinTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    @DisplayName("Resources stay readable after the pinned jar is deleted")
    fun resourcesSurviveJarDeletion() {
        // プラグインの JAR の代わりに、PluginJarPin 自身のクラスとリソースを含む JAR を作る
        val jar = tempDir.resolve("plugin.jar")
        val classEntry = PluginJarPin::class.java.name.replace('.', '/') + ".class"
        val classBytes = PluginJarPin::class.java.classLoader.getResourceAsStream(classEntry)!!.use { it.readAllBytes() }
        val resource = "hello".toByteArray()
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            out.putNextEntry(JarEntry(classEntry))
            out.write(classBytes)
            out.putNextEntry(JarEntry("data/resource.txt"))
            out.write(resource)
        }

        // テストのクラスパスの PluginJarPin を使わないよう、親には Kotlin の標準ライブラリだけを持たせる
        val stdlib = URLClassLoader(arrayOf(Unit::class.java.protectionDomain.codeSource.location), null)
        // JAR の中の PluginJarPin で自分自身の JAR を固定する
        URLClassLoader(arrayOf(jar.toUri().toURL()), stdlib).use { loader ->
            val pin = loader.loadClass(PluginJarPin::class.java.name)
            val instance = pin.getField("INSTANCE").get(null)
            val pinned = pin.getMethod("pin", Class::class.java).invoke(instance, pin)
            assertNotNull(pinned)

            Files.delete(jar)

            // 削除後もリソースを読める
            val read = loader.getResourceAsStream("data/resource.txt")!!.use { it.readAllBytes() }
            assertArrayEquals(resource, read)
        }
    }
}
