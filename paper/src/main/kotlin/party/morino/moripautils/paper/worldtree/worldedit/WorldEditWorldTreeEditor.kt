/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.worldtree.worldedit

import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extension.input.InputParseException
import com.sk89q.worldedit.extension.input.ParserContext
import com.sk89q.worldedit.function.mask.BlockTypeMask
import com.sk89q.worldedit.function.pattern.RandomPattern
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.world.World
import com.sk89q.worldedit.world.block.BaseBlock
import com.sk89q.worldedit.world.block.BlockType
import com.sk89q.worldedit.world.block.BlockTypes
import party.morino.moripautils.common.model.config.BlockPosition
import party.morino.moripautils.common.model.config.WeightedBlock
import party.morino.moripautils.common.model.config.WorldTreeConfig
import party.morino.moripautils.common.model.config.WorldTreeSeason
import party.morino.moripautils.common.model.worldtree.WorldTreeDecision
import party.morino.moripautils.common.model.worldtree.WorldTreeRevertCheck
import party.morino.moripautils.common.worldtree.WorldTreeBlockIds
import party.morino.moripautils.common.worldtree.WorldTreePlanner
import party.morino.moripautils.paper.model.worldtree.ResolvedWorldTreeBlocks
import party.morino.moripautils.paper.model.worldtree.WorldTreeApplyResult

/**
 * WorldEdit / FAWE を使って世界樹の葉を置き換える
 *
 * 管理者が手作業で行っていた //replace と同じ置き換えを、安全確認を挟みながら行う。
 * 1. 範囲内を 1 回だけ数え上げ、葉の数が期待した数と一致するかを確かめる (一致しなければ何も変更しない)
 * 2. すべての季節のブロックを中間ブロックへ戻す
 * 3. 数え直して、季節のブロックが残っていないことと中間ブロックの数を確かめる (失敗したら中間ブロックのまま止める)
 * 4. 中間ブロックを目的の季節のブロックへ置き換える
 *
 * WorldEdit は任意依存のため、WorldEdit の API クラスに触れるコードはこのクラスに閉じ込める。
 * 呼び出し側は WorldEdit (または FAWE) が有効であることを Bukkit の API で確認してからこのクラスを使い、
 * メインスレッドから呼ぶこと。
 */
class WorldEditWorldTreeEditor {
    /**
     * 世界樹の葉を目的の季節にする
     *
     * @param bukkitWorld 世界樹があるワールド
     * @param config 世界樹の設定
     * @param target 目的の季節
     * @param force すでに目的の季節でも置き直すかどうか
     * @return 置き換えの結果
     */
    fun apply(
        bukkitWorld: org.bukkit.World,
        config: WorldTreeConfig,
        target: WorldTreeSeason,
        force: Boolean,
    ): WorldTreeApplyResult {
        val world = BukkitAdapter.adapt(bukkitWorld)
        val region = CuboidRegion(world, config.pos1.toVector(), config.pos2.toVector())
        // 書き込みより前に、設定のブロックをすべて解決できることを確かめる (存在しないブロックがあれば何も変更しない)
        val errors = mutableListOf<String>()
        val blocks = resolveBlocks(world, config, target, errors) ?: return WorldTreeApplyResult.InvalidBlocks(errors)

        // 1. 数え上げて、置き換えてよいかを判断する
        when (val decision = WorldTreePlanner.decide(config, countBlocks(world, region), target, force)) {
            is WorldTreeDecision.Abort -> return WorldTreeApplyResult.CountMismatch(target.name, decision)
            WorldTreeDecision.Skip -> return WorldTreeApplyResult.AlreadyApplied(target.name)
            WorldTreeDecision.Proceed -> Unit
        }

        // 2. どの季節になっていても、必ず中間ブロックへ戻してから置き換える (セッションを閉じて書き込みを反映する)
        val reverted = newSession(world).use { session ->
            session.replaceBlocks(region, BlockTypeMask(session, blocks.seasonTypes), blocks.intermediatePattern)
        }

        // 3. 新しいセッションで数え直し、戻しきれているかを確かめる
        val check = WorldTreePlanner.checkReverted(config, countBlocks(world, region))
        if (check is WorldTreeRevertCheck.Failed) {
            return WorldTreeApplyResult.RevertCheckFailed(target.name, check)
        }

        // 4. 中間ブロックを目的の季節のブロックへ置き換える
        val placed = newSession(world).use { session ->
            session.replaceBlocks(region, BlockTypeMask(session, blocks.intermediateTypes), blocks.targetPattern)
        }
        return WorldTreeApplyResult.Applied(target.name, reverted, placed)
    }

    /**
     * 範囲内のブロックを種類ごとに数える
     *
     * 状態 (葉の distance など) は区別せず、種類の id (名前空間付き) ごとに合計する。
     * 書き込みを反映した後の状態を読むため、呼ぶたびに新しいセッションを使う。
     *
     * @param world 対象のワールド
     * @param region 数える範囲
     * @return 種類の id ごとの数
     */
    private fun countBlocks(world: World, region: Region): Map<String, Int> = newSession(world).use { session ->
        // separateStates = false でも、返される状態に頼らず種類の id で集計し直す
        session.getBlockDistribution(region, false)
            .groupingBy { it.id.blockType.id() }
            .fold(0) { total, countable -> total + countable.amount }
    }

    /**
     * 置き換えに使うセッションを作る
     *
     * 世界樹の範囲は約 130 万ブロックあるため、WorldEdit の設定による変更数の上限を外す。
     *
     * @param world 対象のワールド
     * @return 新しいセッション (use で閉じると変更が反映される)
     */
    private fun newSession(world: World): EditSession =
        WorldEdit.getInstance().newEditSessionBuilder().world(world).maxBlocks(UNLIMITED_BLOCKS).build()

    /**
     * 設定のブロックを WorldEdit の型に解決する
     *
     * すべての季節のパレットも解釈し、書き間違いがあれば季節に関係なく中止する。
     *
     * @param world 対象のワールド (ブロックの解釈に使う)
     * @param config 世界樹の設定
     * @param target 目的の季節
     * @param errors 解決できなかったブロックと理由を追加するリスト
     * @return 解決したブロック。1 つでも解決できなければ null
     */
    private fun resolveBlocks(
        world: World,
        config: WorldTreeConfig,
        target: WorldTreeSeason,
        errors: MutableList<String>,
    ): ResolvedWorldTreeBlocks? {
        val context = parserContext(world)
        val intermediateTypes = config.intermediateTypeIds.mapNotNull { resolveType(it, errors) }
        val seasonTypes = config.seasonTypeIds.mapNotNull { resolveType(it, errors) }
        // 中間ブロックは手作業の //replace と同じく、同じ割合で置く
        val intermediatePattern = buildPattern(config.intermediateBlocks.map { WeightedBlock(it, 1) }, context, errors)
        // 目的の季節以外のパレットも、書き間違いを早めに見つけるために解釈だけしておく
        config.seasons.filter { it != target }.forEach { season -> buildPattern(season.palette, context, errors) }
        val targetPattern = buildPattern(target.palette, context, errors)
        if (errors.isNotEmpty()) return null
        return ResolvedWorldTreeBlocks(intermediateTypes, seasonTypes, intermediatePattern, targetPattern)
    }

    /**
     * 種類の id からブロックの種類を引く
     *
     * @param typeId 名前空間付きの種類の id
     * @param errors 見つからなかった場合に理由を追加するリスト
     * @return ブロックの種類。このサーバーのバージョンに存在しなければ null
     */
    private fun resolveType(typeId: String, errors: MutableList<String>): BlockType? {
        val type: BlockType? = BlockTypes.get(typeId)
        // 新しいバージョンで追加されたブロック (ポプラの葉など) は、古いサーバーでは見つからない
        if (type == null) {
            errors.add("$typeId: unknown block type")
        }
        return type
    }

    /**
     * 重み付きのブロックからランダムに置くパターンを作る
     *
     * @param palette 重み付きのブロック
     * @param context ブロックの解釈に使うコンテキスト
     * @param errors 解釈できなかった場合に理由を追加するリスト
     * @return 重みに応じてランダムに置くパターン
     */
    private fun buildPattern(palette: List<WeightedBlock>, context: ParserContext, errors: MutableList<String>): RandomPattern {
        val pattern = RandomPattern()
        palette.forEach { entry ->
            val block = parseBlock(entry.block, context, errors) ?: return@forEach
            pattern.add(block, entry.weight.toDouble())
        }
        return pattern
    }

    /**
     * WorldEdit の書式のブロックを解釈する
     *
     * @param input WorldEdit の書式のブロック (例: `cherry_leaves[persistent=true]`)
     * @param context ブロックの解釈に使うコンテキスト
     * @param errors 解釈できなかった場合に理由を追加するリスト
     * @return 解釈したブロック。解釈できなければ null
     */
    private fun parseBlock(input: String, context: ParserContext, errors: MutableList<String>): BaseBlock? {
        val block = try {
            WorldEdit.getInstance().blockFactory.parseFromInput(input, context)
        } catch (e: InputParseException) {
            errors.add("$input: ${e.message}")
            return null
        }
        // 別名などで種類が変わると、数え上げやマスクの対象と食い違うため受け付けない
        val expectedType = WorldTreeBlockIds.typeIdOf(input)
        val actualType = block.blockType.id()
        if (actualType != expectedType) {
            errors.add("$input: resolved to $actualType instead of $expectedType")
            return null
        }
        return block
    }

    /**
     * プレイヤーに依存しないブロックの解釈用コンテキストを作る
     *
     * コンソールや起動時にも使うため、プレイヤー (Actor) を設定せず、権限による制限も行わない。
     *
     * @param world 対象のワールド
     * @return コンテキスト
     */
    private fun parserContext(world: World): ParserContext = ParserContext().apply {
        this.world = world
        extent = world
        // 権限による使用禁止ブロックの制限は、実行者のいない自動処理には当てはまらない
        isRestricted = false
        // 状態を省略したブロックは既定の状態で置く (//replace と同じ)
        isPreferringWildcard = false
        // 数値 id などの旧形式は受け付けない
        setTryLegacy(false)
    }

    /**
     * 設定の座標を WorldEdit の座標にする
     *
     * @return WorldEdit の座標
     */
    private fun BlockPosition.toVector(): BlockVector3 = BlockVector3.at(x, y, z)

    companion object {
        /** WorldEdit のセッションで変更数を制限しないことを表す値 */
        private const val UNLIMITED_BLOCKS: Int = -1
    }
}
