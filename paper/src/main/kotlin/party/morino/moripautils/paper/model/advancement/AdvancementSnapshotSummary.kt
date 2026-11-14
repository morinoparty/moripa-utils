/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.model.advancement

import java.time.OffsetDateTime
import java.util.UUID

/**
 * HuskSync のスナップショット 1 件の概要 (Tab 補完の表示と、番号からスナップショットを選ぶために使う)
 *
 * HuskSync のクラスはブートストラップ段階では読み込めないため、コマンド側ではこの型だけを扱う。
 *
 * @property index 新しい順の番号 (最新が 1)
 * @property id スナップショットの ID
 * @property timestamp 保存された日時
 * @property saveCause 保存された理由 (DISCONNECT など)
 * @property pinned 固定されているかどうか
 * @property advancementCount 進捗の数 (レシピの解除を除く。条件を一部だけ満たした進捗も含む)。進捗のデータが無い場合は null
 */
data class AdvancementSnapshotSummary(
    val index: Int,
    val id: UUID,
    val timestamp: OffsetDateTime,
    val saveCause: String,
    val pinned: Boolean,
    val advancementCount: Int?,
)

/**
 * プレイヤーのスナップショットの一覧
 *
 * @property playerId プレイヤーの UUID
 * @property playerName HuskSync に記録されているプレイヤー名
 * @property snapshots スナップショットの概要 (新しい順)
 */
data class AdvancementSnapshotListing(
    val playerId: UUID,
    val playerName: String,
    val snapshots: List<AdvancementSnapshotSummary>,
)
