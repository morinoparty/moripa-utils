/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.schematic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream

class SpongeSchematicV3FormatTest {
    /**
     * gzip 圧縮した NBT を組み立てる
     *
     * @param rootName ルートの Compound の名前
     * @param writeBody ルートの中身を書き出す処理 (末尾の TAG_END はこの関数が書く)
     * @return 組み立てたバイト列
     */
    private fun gzippedNbt(rootName: String, writeBody: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeByte(10)
            out.writeUTF(rootName)
            out.writeBody()
            out.writeByte(0)
        }
        return bytes.toByteArray()
    }

    @Test
    @DisplayName("Accepts Sponge v3 with other tags before Version")
    fun acceptsSpongeV3() {
        val content = gzippedNbt("") {
            // Schematic の前にある無関係なタグも読み飛ばせる
            writeByte(9)
            writeUTF("Unrelated")
            writeByte(3)
            writeInt(2)
            writeInt(1)
            writeInt(2)
            writeByte(10)
            writeUTF("Schematic")
            writeByte(7)
            writeUTF("Data")
            writeInt(3)
            write(byteArrayOf(1, 2, 3))
            writeByte(3)
            writeUTF("Version")
            writeInt(3)
            writeByte(0)
        }

        assertTrue(SpongeSchematicV3Format.isValid(content))
    }

    @Test
    @DisplayName("Rejects Sponge v2 whose root is the Schematic compound")
    fun rejectsSpongeV2() {
        // v2 は Schematic という名前のルート直下に Version = 2 を持つ
        val content = gzippedNbt("Schematic") {
            writeByte(3)
            writeUTF("Version")
            writeInt(2)
        }

        assertFalse(SpongeSchematicV3Format.isValid(content))
    }

    @Test
    @DisplayName("Rejects content that is not gzip")
    fun rejectsNonGzip() {
        assertFalse(SpongeSchematicV3Format.isValid("not a schematic".toByteArray()))
    }
}
