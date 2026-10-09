/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.schematic

import party.morino.moripautils.common.model.schematic.SchematicFormat
import party.morino.moripautils.common.model.schematic.SchematicWorldSize
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * バイト列が Sponge schematic v3 (.schem) かどうかを判定し、範囲の大きさを読み取る
 *
 * Sponge schematic v3 は gzip 圧縮した NBT で、ルートの Compound 直下に Schematic という Compound があり、
 * その中の Version (Int) が 3 になっている。範囲の大きさは同じ Compound の Width / Height / Length (Short) にある。
 * WorldEdit と FAWE では形式判定 (ClipboardFormat.isFormat) の実装が異なり、FAWE の Sponge v3 は常に false を返すため、
 * WorldEdit に頼らず NBT を先頭から読んで判定する。ブロックのデータなどの中身は読み飛ばし、メモリ上には展開しない。
 */
object SpongeSchematicV3Format : SchematicFormatReader {
    override val format: SchematicFormat = SchematicFormat.SPONGE_V3

    /** Sponge schematic v3 の Version の値 */
    private const val VERSION: Int = 3

    /** Version を持つ Compound の名前 */
    private const val SCHEMATIC_TAG: String = "Schematic"

    /** バージョンを表す Int の名前 */
    private const val VERSION_TAG: String = "Version"

    /** 範囲の大きさを表す Short の名前 (X / Y / Z 方向) */
    private const val WIDTH_TAG: String = "Width"
    private const val HEIGHT_TAG: String = "Height"
    private const val LENGTH_TAG: String = "Length"

    /** 入れ子の深さの上限 (不正なデータでスタックを使い切らないようにする) */
    private const val MAX_DEPTH: Int = 512

    // NBT のタグの種類 (https://minecraft.wiki/w/NBT_format)
    private const val TAG_END = 0
    private const val TAG_BYTE = 1
    private const val TAG_SHORT = 2
    private const val TAG_INT = 3
    private const val TAG_LONG = 4
    private const val TAG_FLOAT = 5
    private const val TAG_DOUBLE = 6
    private const val TAG_BYTE_ARRAY = 7
    private const val TAG_STRING = 8
    private const val TAG_LIST = 9
    private const val TAG_COMPOUND = 10
    private const val TAG_INT_ARRAY = 11
    private const val TAG_LONG_ARRAY = 12

    /**
     * バイト列が Sponge schematic v3 かどうかを判定する
     *
     * @param content 判定するバイト列
     * @return Sponge schematic v3 なら true (gzip でない、NBT が壊れている、v1 / v2 などの場合は false)
     */
    fun isValid(content: ByteArray): Boolean = readWorldSize(content) != null

    /**
     * Sponge schematic v3 の範囲の大きさを読み取る
     *
     * @param content 読み取るバイト列
     * @return 範囲の大きさ。Sponge schematic v3 でない、または大きさが書かれていない場合は null
     */
    override fun readWorldSize(content: ByteArray): SchematicWorldSize? = try {
        DataInputStream(GZIPInputStream(ByteArrayInputStream(content))).use { input -> readRoot(input) }
    } catch (_: IOException) {
        // gzip でない (ZipException)、途中で途切れている (EOFException) などはすべて不正な schematic とみなす
        null
    }

    /**
     * ルートの Compound を読み、Schematic の Compound から範囲の大きさを読み取る
     *
     * @param input gzip を展開した NBT
     * @return 範囲の大きさ。Sponge schematic v3 でない場合は null
     */
    private fun readRoot(input: DataInputStream): SchematicWorldSize? {
        if (input.readByte().toInt() != TAG_COMPOUND) return null
        // ルートの名前は v3 では空文字だが、判定には使わない
        input.readUTF()
        while (true) {
            val type = input.readByte().toInt()
            if (type == TAG_END) return null
            val name = input.readUTF()
            if (type == TAG_COMPOUND && name == SCHEMATIC_TAG) {
                return readSchematic(input)
            }
            skipPayload(input, type, depth = 1)
        }
    }

    /**
     * Schematic の Compound から Version と範囲の大きさを読み取る
     *
     * タグの順番は決まっていないため、Compound の終わりまで読んでから判定する。
     *
     * @param input Schematic の Compound の中身の先頭を指す NBT
     * @return 範囲の大きさ。Version が 3 でない、または大きさが欠けている場合は null
     */
    private fun readSchematic(input: DataInputStream): SchematicWorldSize? {
        var version: Int? = null
        var width: Int? = null
        var height: Int? = null
        var length: Int? = null
        while (true) {
            val type = input.readByte().toInt()
            if (type == TAG_END) break
            val name = input.readUTF()
            when {
                type == TAG_INT && name == VERSION_TAG -> version = input.readInt()
                // Width / Height / Length は符号なしの Short として扱う (最大 65535)
                type == TAG_SHORT && name == WIDTH_TAG -> width = input.readUnsignedShort()
                type == TAG_SHORT && name == HEIGHT_TAG -> height = input.readUnsignedShort()
                type == TAG_SHORT && name == LENGTH_TAG -> length = input.readUnsignedShort()
                else -> skipPayload(input, type, depth = 2)
            }
        }
        if (version != VERSION || width == null || height == null || length == null) return null
        return SchematicWorldSize(width, height, length)
    }

    /**
     * タグの中身を読み飛ばす
     *
     * @param input タグの中身の先頭を指す NBT
     * @param type タグの種類
     * @param depth 現在の入れ子の深さ
     * @throws IOException 未知のタグ、入れ子が深すぎる、または途中で途切れている場合
     */
    private fun skipPayload(input: DataInputStream, type: Int, depth: Int) {
        if (depth > MAX_DEPTH) throw IOException("NBT is nested too deeply")
        when (type) {
            TAG_BYTE -> input.skipNBytes(Byte.SIZE_BYTES.toLong())
            TAG_SHORT -> input.skipNBytes(Short.SIZE_BYTES.toLong())
            TAG_INT, TAG_FLOAT -> input.skipNBytes(Int.SIZE_BYTES.toLong())
            TAG_LONG, TAG_DOUBLE -> input.skipNBytes(Long.SIZE_BYTES.toLong())
            TAG_BYTE_ARRAY -> input.skipNBytes(input.readInt().toLong())
            TAG_STRING -> input.skipNBytes(input.readUnsignedShort().toLong())
            TAG_LIST -> {
                // List は要素の種類と件数のあとに、名前のない要素が並ぶ
                val elementType = input.readByte().toInt()
                repeat(input.readInt()) { skipPayload(input, elementType, depth + 1) }
            }
            TAG_COMPOUND -> {
                // Compound は名前付きのタグが TAG_END まで並ぶ
                while (true) {
                    val childType = input.readByte().toInt()
                    if (childType == TAG_END) break
                    input.readUTF()
                    skipPayload(input, childType, depth + 1)
                }
            }
            TAG_INT_ARRAY -> input.skipNBytes(Int.SIZE_BYTES.toLong() * input.readInt())
            TAG_LONG_ARRAY -> input.skipNBytes(Long.SIZE_BYTES.toLong() * input.readInt())
            else -> throw IOException("Unknown NBT tag type: $type")
        }
    }
}
