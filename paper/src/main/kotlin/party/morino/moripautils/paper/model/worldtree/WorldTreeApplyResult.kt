/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.model.worldtree

import party.morino.moripautils.common.model.worldtree.WorldTreeDecision
import party.morino.moripautils.common.model.worldtree.WorldTreeRevertCheck

/**
 * 世界樹の季節の切り替えの結果
 */
sealed interface WorldTreeApplyResult {
    /**
     * 中間ブロックへ戻してから季節のブロックへ置き換えた
     *
     * @property season 置いた季節の名前
     * @property revertedBlocks 中間ブロックへ戻したブロックの数
     * @property placedBlocks 季節のブロックを置いた数
     */
    data class Applied(val season: String, val revertedBlocks: Int, val placedBlocks: Int) : WorldTreeApplyResult

    /**
     * すでに目的の季節になっていたため、何も変更しなかった
     *
     * @property season 目的の季節の名前
     */
    data class AlreadyApplied(val season: String) : WorldTreeApplyResult

    /**
     * 葉の数が期待した数と一致しなかったため、何も変更しなかった
     *
     * @property season 目的の季節の名前
     * @property decision 数え上げの結果
     */
    data class CountMismatch(val season: String, val decision: WorldTreeDecision.Abort) : WorldTreeApplyResult

    /**
     * 中間ブロックへ戻した後の確認に失敗したため、中間ブロックのまま止めた
     *
     * @property season 目的の季節の名前
     * @property check 確認の結果
     */
    data class RevertCheckFailed(val season: String, val check: WorldTreeRevertCheck.Failed) : WorldTreeApplyResult

    /**
     * 設定のブロックを WorldEdit が解釈できなかったため、何も変更しなかった
     *
     * @property errors 解釈できなかったブロックと理由
     */
    data class InvalidBlocks(val errors: List<String>) : WorldTreeApplyResult

    /**
     * 指定した名前の季節が設定に無い
     *
     * @property name 指定された名前
     */
    data class UnknownSeason(val name: String) : WorldTreeApplyResult

    /**
     * 設定のワールドが読み込まれていない
     *
     * @property world ワールドの名前
     */
    data class WorldNotFound(val world: String) : WorldTreeApplyResult

    /** WorldEdit (または FAWE) が有効でない、または API に互換性が無い */
    data object WorldEditUnavailable : WorldTreeApplyResult

    /**
     * 予期しない例外で失敗した (途中まで置き換わっている可能性がある)
     *
     * @property message 例外の内容
     */
    data class Failed(val message: String) : WorldTreeApplyResult
}
