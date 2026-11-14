/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.advancement

import party.morino.moripautils.paper.model.advancement.AdvancementSnapshotSummary
import java.util.UUID

/**
 * コマンドの <snapshot> 引数の指定
 */
sealed interface SnapshotSelector {
    /**
     * 新しい順の番号で指定した (最新が 1)
     *
     * @property index 番号
     */
    data class ByIndex(val index: Int) : SnapshotSelector

    /**
     * スナップショットの ID で指定した
     *
     * @property id スナップショットの ID
     */
    data class ById(val id: UUID) : SnapshotSelector

    /**
     * 一覧からスナップショットを選ぶ
     *
     * @param snapshots スナップショットの概要 (新しい順)
     * @return 選ばれたスナップショット、見つからない場合は null
     */
    fun select(snapshots: List<AdvancementSnapshotSummary>): AdvancementSnapshotSummary? = when (this) {
        is ByIndex -> snapshots.getOrNull(index - 1)
        is ById -> snapshots.firstOrNull { it.id == id }
    }

    companion object {
        /**
         * 引数の文字列を解釈する
         *
         * 正の整数なら番号、そうでなければ UUID として解釈する。
         *
         * @param input 引数の文字列
         * @return 解釈した指定、どちらとしても解釈できない場合は null
         */
        fun parse(input: String): SnapshotSelector? {
            val trimmed = input.trim()
            trimmed.toIntOrNull()?.let { index -> return if (index >= 1) ByIndex(index) else null }
            return runCatching { UUID.fromString(trimmed) }.getOrNull()?.let(::ById)
        }
    }
}
