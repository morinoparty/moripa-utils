/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.model.worldtree

/**
 * 中間ブロックへ戻した後の確認結果
 */
sealed interface WorldTreeRevertCheck {
    /** 季節のブロックが残っておらず、中間ブロックが期待した数だけある */
    data object Passed : WorldTreeRevertCheck

    /**
     * 戻しきれていない、または中間ブロックの数が合わない
     *
     * @property remainingSeasonBlocks 残っている季節のブロックの種類ごとの数 (0 の種類は含まない)
     * @property intermediateCount 中間ブロックの合計
     * @property expectedCount 設定で期待した数
     */
    data class Failed(
        val remainingSeasonBlocks: Map<String, Int>,
        val intermediateCount: Int,
        val expectedCount: Int,
    ) : WorldTreeRevertCheck
}
