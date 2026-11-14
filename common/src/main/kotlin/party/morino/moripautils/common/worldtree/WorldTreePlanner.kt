/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.worldtree

import party.morino.moripautils.common.model.config.WorldTreeConfig
import party.morino.moripautils.common.model.config.WorldTreeSeason
import party.morino.moripautils.common.model.worldtree.WorldTreeDecision
import party.morino.moripautils.common.model.worldtree.WorldTreeRevertCheck

/**
 * 範囲内のブロックの数え上げ (種類の id → 数) から、世界樹の葉を置き換えてよいかを判断する
 *
 * WorldEdit に依存しない純粋な処理なので、単体テストで分岐を確かめられる。
 * 数え上げのキーは [WorldTreeBlockIds.typeIdOf] と同じ名前空間付きの id であること。
 */
object WorldTreePlanner {
    /**
     * 置き換えを始める前の判定を行う
     *
     * 1. 中間ブロックとすべての季節のブロックの合計 (種類ごとに 1 回だけ数える) が期待した数と一致しなければ中止する
     * 2. 強制でなく、目的の季節のブロックだけで期待した数に達していれば、すでに反映済みとして省略する
     * 3. それ以外は、中間ブロックへ戻してから目的の季節へ置き換える
     *
     * @param config 世界樹の設定
     * @param histogram 範囲内のブロックの種類ごとの数
     * @param target 目的の季節
     * @param force 同じ季節でも置き直すかどうか
     * @return 判定の結果
     */
    fun decide(
        config: WorldTreeConfig,
        histogram: Map<String, Int>,
        target: WorldTreeSeason,
        force: Boolean,
    ): WorldTreeDecision {
        // 季節をまたいで共通する種類 (白の色付きガラスなど) は Set にまとめて二重に数えない
        val counts = config.trackedTypeIds.associateWith { histogram[it] ?: 0 }
        val trackedCount = counts.values.sum()
        // 葉の数が合わない場合は、範囲や設定の誤り、または手作業の編集が考えられるため何も変更しない
        if (trackedCount != config.expectedBlockCount) {
            return WorldTreeDecision.Abort(trackedCount, config.expectedBlockCount, counts)
        }
        // 目的の季節の種類だけで期待した数に達していれば、すでにその季節になっている
        val targetCount = target.typeIds.sumOf { histogram[it] ?: 0 }
        if (!force && targetCount == config.expectedBlockCount) {
            return WorldTreeDecision.Skip
        }
        return WorldTreeDecision.Proceed
    }

    /**
     * 中間ブロックへ戻した後の状態を確かめる
     *
     * 季節のブロックが 1 つでも残っている場合や、中間ブロックの数が期待した数と異なる場合は失敗とし、
     * 季節のブロックへの置き換えに進まない。
     *
     * @param config 世界樹の設定
     * @param histogram 戻した後に数え直した、範囲内のブロックの種類ごとの数
     * @return 確認の結果
     */
    fun checkReverted(config: WorldTreeConfig, histogram: Map<String, Int>): WorldTreeRevertCheck {
        // 残っている季節のブロックだけを、原因の調査用に集める
        val remaining = config.seasonTypeIds
            .associateWith { histogram[it] ?: 0 }
            .filterValues { it > 0 }
        val intermediateCount = config.intermediateTypeIds.sumOf { histogram[it] ?: 0 }
        return if (remaining.isEmpty() && intermediateCount == config.expectedBlockCount) {
            WorldTreeRevertCheck.Passed
        } else {
            WorldTreeRevertCheck.Failed(remaining, intermediateCount, config.expectedBlockCount)
        }
    }
}
