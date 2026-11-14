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
 * 世界樹の葉を置き換える前の判定結果 (範囲内のブロックの数え上げから決める)
 */
sealed interface WorldTreeDecision {
    /**
     * 葉の数が期待した数と一致しないため、何も変更せずに中止する
     *
     * @property trackedCount 中間ブロックとすべての季節のブロックの合計 (種類ごとに 1 回だけ数える)
     * @property expectedCount 設定で期待した数
     * @property counts 種類ごとの数 (数が 0 の種類も含む。ログに出して原因を調べるために使う)
     */
    data class Abort(
        val trackedCount: Int,
        val expectedCount: Int,
        val counts: Map<String, Int>,
    ) : WorldTreeDecision

    /** すでに目的の季節のブロックだけになっているため、置き換えを省略する */
    data object Skip : WorldTreeDecision

    /** 中間ブロックへ戻してから目的の季節へ置き換える */
    data object Proceed : WorldTreeDecision
}
