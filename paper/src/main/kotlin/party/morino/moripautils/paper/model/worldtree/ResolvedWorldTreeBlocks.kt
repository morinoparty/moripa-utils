/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.model.worldtree

import com.sk89q.worldedit.function.pattern.Pattern
import com.sk89q.worldedit.world.block.BlockType

/**
 * WorldEdit の型に解決した世界樹のブロック
 *
 * WorldEdit の型を持つため、WorldEdit (または FAWE) が有効であることを確認してから
 * [party.morino.moripautils.paper.worldtree.worldedit.WorldEditWorldTreeEditor] の中でだけ使うこと。
 *
 * @property intermediateTypes 中間ブロックの種類 (季節のブロックへ置き換えるときのマスク)
 * @property seasonTypes すべての季節のブロックの種類 (中間ブロックへ戻すときのマスク)
 * @property intermediatePattern 中間ブロックを同じ割合でランダムに置くパターン
 * @property targetPattern 目的の季節のブロックを重みに応じてランダムに置くパターン
 */
data class ResolvedWorldTreeBlocks(
    val intermediateTypes: List<BlockType>,
    val seasonTypes: List<BlockType>,
    val intermediatePattern: Pattern,
    val targetPattern: Pattern,
)
