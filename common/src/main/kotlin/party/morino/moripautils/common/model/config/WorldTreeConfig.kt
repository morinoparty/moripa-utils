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
import java.time.DateTimeException
import java.time.ZoneId

/**
 * 世界樹 (world tree 機能) の設定
 *
 * ロビーの世界樹の葉は、季節に依存しない「中間ブロック」([intermediateBlocks]) で作られている。
 * サーバーの起動時に現在の月から季節を選び、葉を必ず中間ブロックへ戻してから季節のブロックへ置き換える。
 *
 * @property enabled world tree 機能を有効にするかどうか (Paper のみ)
 * @property world 世界樹があるワールドの名前
 * @property pos1 世界樹を囲む範囲の角の 1 つ
 * @property pos2 世界樹を囲む範囲の、pos1 と対角にある角
 * @property timeZone 現在の月を求めるタイムゾーン (例: Asia/Tokyo)
 * @property expectedBlockCount 範囲内にある葉 (中間ブロックとすべての季節のブロックの合計) の数。一致しない場合は何も変更しない
 * @property intermediateBlocks 中間ブロックの種類 (季節を戻すときはこれらを同じ割合でランダムに置く)
 * @property seasons 季節の一覧 (1 月から 12 月のそれぞれが、ちょうど 1 つの季節に割り当てられている必要がある)
 * @throws IllegalArgumentException 値の検証 (init ブロックの各条件) に失敗した場合
 */
@Serializable
data class WorldTreeConfig(
    val enabled: Boolean = false,
    val world: String = DEFAULT_WORLD,
    val pos1: BlockPosition = DEFAULT_POS1,
    val pos2: BlockPosition = DEFAULT_POS2,
    val timeZone: String = DEFAULT_TIME_ZONE,
    val expectedBlockCount: Int = DEFAULT_EXPECTED_BLOCK_COUNT,
    val intermediateBlocks: List<String> = DEFAULT_INTERMEDIATE_BLOCKS,
    val seasons: List<WorldTreeSeason> = DEFAULT_SEASONS,
) {
    init {
        // ワールド名が空だと対象のワールドを特定できない
        require(world.isNotBlank()) { "worldTree.world must not be blank" }
        // 期待するブロック数は安全確認に使うため、正の値でなければならない
        require(expectedBlockCount > 0) { "worldTree.expectedBlockCount must be greater than 0, but was $expectedBlockCount" }
        // ZoneId.of は DateTimeException を投げるため、設定ローダーが扱える IllegalArgumentException に変換する
        require(isValidZoneId(timeZone)) { "worldTree.timeZone must be a valid time zone id, but was '$timeZone'" }
        validateIntermediateBlocks()
        validateSeasons()
    }

    /**
     * 中間ブロックの指定を検証する
     *
     * @throws IllegalArgumentException 空、形式が不正、または種類が重複している場合
     */
    private fun validateIntermediateBlocks() {
        // 中間ブロックが無いと季節を戻せない
        require(intermediateBlocks.isNotEmpty()) { "worldTree.intermediateBlocks must not be empty" }
        val invalid = intermediateBlocks.filterNot { WorldTreeBlockIds.isValid(it) }
        require(invalid.isEmpty()) { "worldTree.intermediateBlocks contains invalid block ids: $invalid" }
        // 同じ種類を重複して書くと、数え上げで二重に数えたり割合が偏ったりする
        val duplicated = intermediateBlocks.groupBy { WorldTreeBlockIds.typeIdOf(it) }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "worldTree.intermediateBlocks must be unique, but duplicated: $duplicated" }
    }

    /**
     * 季節の一覧を検証する
     *
     * @throws IllegalArgumentException 名前の重複、月の割り当ての漏れや重複、中間ブロックとの重なりがある場合
     */
    private fun validateSeasons() {
        // 名前はコマンドで季節を選ぶキーなので重複を許さない
        val duplicatedNames = seasons.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicatedNames.isEmpty()) { "worldTree.seasons names must be unique, but duplicated: $duplicatedNames" }
        // どの月でも季節が 1 つに決まるよう、1 月から 12 月をちょうど 1 回ずつ割り当てる
        val assignedMonths = seasons.flatMap { it.months }
        val duplicatedMonths = assignedMonths.groupBy { it }.filterValues { it.size > 1 }.keys
        require(duplicatedMonths.isEmpty()) { "worldTree.seasons months must not overlap, but duplicated: $duplicatedMonths" }
        val missingMonths = WorldTreeSeason.MONTH_RANGE.filterNot { it in assignedMonths }
        require(missingMonths.isEmpty()) { "worldTree.seasons must cover every month, but missing: $missingMonths" }
        // 中間ブロックと季節のブロックが重なると、戻したのか置いたのかを数え上げで区別できない
        val overlapping = intermediateTypeIds.intersect(seasonTypeIds)
        require(overlapping.isEmpty()) { "worldTree season blocks must not be intermediate blocks, but overlapped: $overlapping" }
    }

    /** 中間ブロックの種類の id (名前空間付き) */
    val intermediateTypeIds: Set<String>
        get() = intermediateBlocks.map { WorldTreeBlockIds.typeIdOf(it) }.toSet()

    /** すべての季節のブロックの種類の id (季節をまたいで共通する種類は 1 回だけ含む) */
    val seasonTypeIds: Set<String>
        get() = seasons.flatMap { it.typeIds }.toSet()

    /** 世界樹の葉として数える種類の id (中間ブロックとすべての季節のブロック) */
    val trackedTypeIds: Set<String>
        get() = intermediateTypeIds + seasonTypeIds

    /** 現在の月を求めるタイムゾーン */
    val zoneId: ZoneId
        get() = ZoneId.of(timeZone)

    /**
     * 名前から季節を探す
     *
     * @param name 季節の名前
     * @return 見つかった季節、見つからなければ null
     */
    fun seasonByName(name: String): WorldTreeSeason? = seasons.firstOrNull { it.name == name }

    companion object {
        /** world の既定値 */
        const val DEFAULT_WORLD: String = "world"

        /** timeZone の既定値 */
        const val DEFAULT_TIME_ZONE: String = "Asia/Tokyo"

        /** expectedBlockCount の既定値 (ロビーの世界樹の葉の数) */
        const val DEFAULT_EXPECTED_BLOCK_COUNT: Int = 19984

        /** pos1 の既定値 (ロビーの世界樹を囲む範囲) */
        val DEFAULT_POS1: BlockPosition = BlockPosition(43, 102, 54)

        /** pos2 の既定値 (ロビーの世界樹を囲む範囲) */
        val DEFAULT_POS2: BlockPosition = BlockPosition(-79, 183, -77)

        /** intermediateBlocks の既定値 */
        val DEFAULT_INTERMEDIATE_BLOCKS: List<String> = listOf("obsidian", "red_wool", "crafting_table", "quartz_block")

        /** seasons の既定値 (3-4 月は春、5-9 月は夏、10-11 月は秋、12-2 月は冬) */
        val DEFAULT_SEASONS: List<WorldTreeSeason> = listOf(
            WorldTreeSeason(
                name = "spring",
                months = listOf(3, 4),
                palette = listOf(
                    WeightedBlock("cherry_leaves[persistent=true]", 60),
                    WeightedBlock("flowering_azalea_leaves[persistent=true]", 15),
                    WeightedBlock("white_stained_glass", 15),
                    WeightedBlock("pink_wool", 10),
                ),
            ),
            WorldTreeSeason(
                name = "summer",
                months = listOf(5, 6, 7, 8, 9),
                palette = listOf(
                    WeightedBlock("oak_leaves[persistent=true]", 35),
                    WeightedBlock("birch_leaves[persistent=true]", 25),
                    WeightedBlock("azalea_leaves[persistent=true]", 25),
                    WeightedBlock("green_wool", 15),
                ),
            ),
            WorldTreeSeason(
                name = "autumn",
                months = listOf(10, 11),
                palette = listOf(
                    WeightedBlock("red_poplar_leaves[persistent=true]", 30),
                    WeightedBlock("orange_poplar_leaves[persistent=true]", 30),
                    WeightedBlock("yellow_poplar_leaves[persistent=true]", 40),
                ),
            ),
            WorldTreeSeason(
                name = "winter",
                months = listOf(12, 1, 2),
                palette = listOf(
                    WeightedBlock("spruce_leaves[persistent=true]", 40),
                    WeightedBlock("powder_snow", 25),
                    WeightedBlock("pale_oak_leaves[persistent=true]", 25),
                    WeightedBlock("white_stained_glass", 10),
                ),
            ),
        )

        /**
         * タイムゾーンの id として解釈できるかどうかを判定する
         *
         * @param id タイムゾーンの id
         * @return 解釈できる場合は true
         */
        private fun isValidZoneId(id: String): Boolean = try {
            ZoneId.of(id)
            true
        } catch (_: DateTimeException) {
            false
        }
    }
}
