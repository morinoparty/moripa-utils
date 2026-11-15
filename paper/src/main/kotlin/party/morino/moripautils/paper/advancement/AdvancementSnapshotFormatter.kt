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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * スナップショットの概要を表示用の文字列にする
 *
 * @param zone 日時を表示するタイムゾーン
 */
class AdvancementSnapshotFormatter(private val zone: ZoneId = ZoneId.systemDefault()) {
    /**
     * 保存された日時を表示用にする
     *
     * @param summary スナップショットの概要
     * @return yyyy-MM-dd HH:mm 形式の日時
     */
    fun timestamp(summary: AdvancementSnapshotSummary): String =
        summary.timestamp.atZoneSameInstant(zone).format(TIMESTAMP_FORMAT)

    /**
     * スナップショットの説明にする (実行時のメッセージに表示する)
     *
     * @param summary スナップショットの概要
     * @return 日時、進捗の数、保存された理由、固定の有無、短い ID を並べた文字列
     */
    fun describe(summary: AdvancementSnapshotSummary): String = buildList {
        add(timestamp(summary))
        add(summary.advancementCount?.let { "進捗 $it" } ?: "進捗なし")
        add(summary.saveCause)
        if (summary.pinned) {
            add("固定")
        }
        add(summary.id.toString().take(SHORT_ID_LENGTH))
    }.joinToString(" / ")

    /**
     * Tab 補完の tooltip に表示する説明にする
     *
     * 候補は ID の順に並ぶため、新しい順の番号を先頭に付ける (候補自体が ID なので短い ID は付けない)。
     *
     * @param summary スナップショットの概要
     * @return 新しい順の番号、日時、進捗の数、保存された理由、固定の有無を並べた文字列
     */
    fun tooltip(summary: AdvancementSnapshotSummary): String = buildList {
        add("#${summary.index} ${timestamp(summary)}")
        add(summary.advancementCount?.let { "進捗 $it" } ?: "進捗なし")
        add(summary.saveCause)
        if (summary.pinned) {
            add("固定")
        }
    }.joinToString(" / ")

    companion object {
        /** 日時の表示形式 */
        private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /** ID を短く表示するときの文字数 (HuskSync の短い ID と同じ) */
        const val SHORT_ID_LENGTH: Int = 8
    }
}
