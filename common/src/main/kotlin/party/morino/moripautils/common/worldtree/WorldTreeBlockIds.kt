/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.worldtree

/**
 * 世界樹の設定に書かれたブロックの文字列から、ブロックの種類の id を取り出す
 *
 * WorldEdit の BlockType#id は `minecraft:oak_leaves` のように名前空間付きで返すため、
 * 設定の `oak_leaves[persistent=true]` などと比較できるよう同じ形に揃える。
 */
object WorldTreeBlockIds {
    /** 名前空間を省略したときに補う既定の名前空間 */
    const val DEFAULT_NAMESPACE: String = "minecraft"

    /** ブロックの種類の id として使える文字列のパターン (名前空間は省略できる) */
    private val TYPE_ID_PATTERN: Regex = Regex("^([a-z0-9_.-]+:)?[a-z0-9_./-]+$")

    /**
     * ブロックの文字列から状態を除き、名前空間付きの種類の id にする
     *
     * @param block WorldEdit の書式のブロック (例: `cherry_leaves[persistent=true]`)
     * @return 名前空間付きの種類の id (例: `minecraft:cherry_leaves`)
     */
    fun typeIdOf(block: String): String {
        // [ 以降はブロックの状態なので、種類の判定には使わない
        val type = block.substringBefore('[').trim().lowercase()
        // 名前空間が無い場合は WorldEdit と同じく minecraft を補う
        return if (':' in type) type else "$DEFAULT_NAMESPACE:$type"
    }

    /**
     * ブロックの文字列が種類の id として読めるかどうかを判定する
     *
     * 状態 ([] の中身) の正しさは WorldEdit が解釈するときに確かめるため、ここでは種類の部分だけを見る。
     *
     * @param block WorldEdit の書式のブロック
     * @return 種類の id として読める場合は true
     */
    fun isValid(block: String): Boolean {
        val type = block.substringBefore('[').trim().lowercase()
        // [ で始まるのに ] で閉じていない場合は書き間違いとみなす
        val statesClosed = '[' !in block || block.trimEnd().endsWith(']')
        return statesClosed && TYPE_ID_PATTERN.matches(type)
    }
}
