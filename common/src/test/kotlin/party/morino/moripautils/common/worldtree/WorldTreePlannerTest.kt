/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.worldtree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.moripautils.common.model.config.WorldTreeConfig
import party.morino.moripautils.common.model.worldtree.WorldTreeDecision
import party.morino.moripautils.common.model.worldtree.WorldTreeRevertCheck

class WorldTreePlannerTest {
    /** 既定の設定 (期待するブロック数は 19984) */
    private val config = WorldTreeConfig()

    /** 季節の名前から季節を取り出す */
    private fun season(name: String) = config.seasonByName(name)!!

    @Test
    @DisplayName("Aborts when the tracked block count differs from the expected count")
    fun abortsOnCountMismatch() {
        // 中間ブロックが 1 つ足りない
        val histogram = mapOf("minecraft:obsidian" to 19983, "minecraft:air" to 100)

        val decision = WorldTreePlanner.decide(config, histogram, season("spring"), force = false)

        assertTrue(decision is WorldTreeDecision.Abort, decision.toString())
        assertEquals(19983, (decision as WorldTreeDecision.Abort).trackedCount)
    }

    @Test
    @DisplayName("Counts block types shared by seasons only once")
    fun countsSharedTypesOnce() {
        // 白の色付きガラスは春と冬の両方に含まれるが、合計には 1 回だけ数える
        val histogram = mapOf(
            "minecraft:cherry_leaves" to 15000,
            "minecraft:white_stained_glass" to 2984,
            "minecraft:pink_wool" to 2000,
        )

        val decision = WorldTreePlanner.decide(config, histogram, season("spring"), force = false)

        assertEquals(WorldTreeDecision.Skip, decision)
    }

    @Test
    @DisplayName("Proceeds when forced even if the target season is already applied")
    fun proceedsWhenForced() {
        val histogram = mapOf("minecraft:cherry_leaves" to 19984)

        assertEquals(WorldTreeDecision.Skip, WorldTreePlanner.decide(config, histogram, season("spring"), force = false))
        assertEquals(WorldTreeDecision.Proceed, WorldTreePlanner.decide(config, histogram, season("spring"), force = true))
    }

    @Test
    @DisplayName("Proceeds when another season is placed")
    fun proceedsFromAnotherSeason() {
        val histogram = mapOf("minecraft:oak_leaves" to 10000, "minecraft:green_wool" to 9984)

        assertEquals(WorldTreeDecision.Proceed, WorldTreePlanner.decide(config, histogram, season("autumn"), force = false))
    }

    @Test
    @DisplayName("Revert check fails when season blocks remain")
    fun revertCheckFailsWithRemainingBlocks() {
        val histogram = mapOf("minecraft:obsidian" to 19980, "minecraft:spruce_leaves" to 4)

        val check = WorldTreePlanner.checkReverted(config, histogram)

        assertEquals(WorldTreeRevertCheck.Failed(mapOf("minecraft:spruce_leaves" to 4), 19980, 19984), check)
    }

    @Test
    @DisplayName("Revert check passes when only intermediate blocks remain")
    fun revertCheckPasses() {
        val histogram = mapOf(
            "minecraft:obsidian" to 5000,
            "minecraft:red_wool" to 5000,
            "minecraft:crafting_table" to 5000,
            "minecraft:quartz_block" to 4984,
        )

        assertEquals(WorldTreeRevertCheck.Passed, WorldTreePlanner.checkReverted(config, histogram))
    }
}
