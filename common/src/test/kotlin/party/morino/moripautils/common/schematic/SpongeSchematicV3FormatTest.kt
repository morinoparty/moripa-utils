/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.schematic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.moripautils.common.model.schematic.SchematicWorldSize
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
    @DisplayName("Reads world size from Sponge v3 regardless of tag order")
    fun readsWorldSizeFromSpongeV3() {
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
            // Width は Short だが符号なしとして読む (40000 は Short では負の値になる)
            writeByte(2)
            writeUTF("Width")
            writeShort(40000)
            writeByte(2)
            writeUTF("Height")
            writeShort(5)
            writeByte(2)
            writeUTF("Length")
            writeShort(7)
            writeByte(0)
        }

        assertEquals(SchematicWorldSize(40000, 5, 7), SpongeSchematicV3Format.readWorldSize(content))
    }

    @Test
    @DisplayName("Rejects Sponge v3 without size tags")
    fun rejectsSpongeV3WithoutSize() {
        val content = gzippedNbt("") {
            writeByte(10)
            writeUTF("Schematic")
            writeByte(3)
            writeUTF("Version")
            writeInt(3)
            writeByte(0)
        }

        assertNull(SpongeSchematicV3Format.readWorldSize(content))
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
