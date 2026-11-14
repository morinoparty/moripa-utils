/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.config

import kotlinx.serialization.Serializable
import party.morino.moripautils.common.worldtree.WorldTreeBlockIds

/**
 * 重み付きのブロック (WorldEdit の `30%red_poplar_leaves[persistent=true]` の 1 要素に相当する)
 *
 * @property block WorldEdit の書式のブロック (例: `cherry_leaves[persistent=true]`)。ブロックの状態は [] で指定できる
 * @property weight 選ばれる重み (同じパレット内の重みの合計に対する割合で選ばれる)。0 より大きい必要がある
 * @throws IllegalArgumentException block の形式が不正な場合、または weight が 0 以下の場合
 */
@Serializable
data class WeightedBlock(
    val block: String,
    val weight: Int,
) {
    init {
        // ブロックの種類が識別子として読めない場合は、置き換えの対象を決められない
        require(WorldTreeBlockIds.isValid(block)) { "worldTree block must be a block id like 'oak_leaves[persistent=true]', but was '$block'" }
        // 重みが 0 以下だと選ばれない、または確率として扱えない
        require(weight > 0) { "worldTree block weight must be greater than 0, but was $weight ($block)" }
    }

    /** ブロックの状態を除いた種類の id (例: `minecraft:cherry_leaves`)。置き換えや数え上げの対象に使う */
    val typeId: String
        get() = WorldTreeBlockIds.typeIdOf(block)
}
