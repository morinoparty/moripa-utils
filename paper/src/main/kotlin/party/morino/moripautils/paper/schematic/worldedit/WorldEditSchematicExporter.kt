/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.schematic.worldedit

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.sk89q.worldedit.EmptyClipboardException
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.WorldEditException
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.math.Vector3
import com.sk89q.worldedit.session.ClipboardHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.entity.Player
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.schematic.SchematicSpawnPosition
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.model.schematic.ExportedClipboard
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * WorldEdit / FAWE のクリップボードを Sponge schematic v3 として書き出す
 *
 * WorldEdit は任意依存のため、WorldEdit の API クラスに触れるコードはこのクラスに閉じ込める。
 * 呼び出し側は WorldEdit (または FAWE) が有効であることを Bukkit の API で確認してからこのクラスを使うこと
 * (確認前に参照すると、WorldEdit が無い環境で NoClassDefFoundError になる)。
 */
class WorldEditSchematicExporter : MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()

    /**
     * プレイヤーのクリップボードを Sponge schematic v3 のバイト列にし、プレイヤーの位置をスポーン位置として添える
     *
     * セッションとプレイヤーの位置の参照はメインスレッドで、変形の反映と書き出しは重いため I/O スレッドで行う。
     *
     * @param player クリップボードを持つプレイヤー
     * @return 書き出した schematic とスポーン位置。クリップボードが空の場合は null
     * @throws IOException 変形の反映や書き出しに失敗した場合、Sponge schematic v3 の形式が見つからない場合
     */
    suspend fun exportClipboard(player: Player): ExportedClipboard? {
        val (holder, location) = withContext(plugin.minecraftDispatcher) {
            // 位置はメインスレッドで読む (書き出し中にプレイヤーが動いても、実行した瞬間の位置を使う)
            clipboardHolderOrNull(player)?.let { it to player.location }
        } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                // //rotate や //flip の変形はホルダーに保持されているだけなので、書き出す前にクリップボードへ反映する
                val transform = holder.transform
                val clipboard = if (transform.isIdentity) holder.clipboard else holder.clipboard.transform(transform)
                val output = ByteArrayOutputStream()
                // ライターを閉じたときに gzip が書き切られるため、use を抜けてからバイト列を取り出す
                spongeV3Format().getWriter(output).use { writer -> writer.write(clipboard) }
                ExportedClipboard(output.toByteArray(), spawnPositionOf(holder, location))
            } catch (e: WorldEditException) {
                // 呼び出し側が WorldEdit の例外型に触れなくて済むよう包み直す
                throw IOException("Failed to transform clipboard: ${e.message}", e)
            }
        }
    }

    /**
     * プレイヤーの位置を、クリップボードの基準点からの相対位置にする
     *
     * Sponge schematic の Offset は基準点 (//copy したときの基準点) からの相対位置なので、同じ基準で表すと
     * 「貼り付けた位置 + スポーン位置」でワールド上の位置が求まる。
     * 変形 (//rotate など) は基準点を中心に回転するため、相対位置にも同じ変形を掛ける。向き (yaw) は変形の影響を反映しない。
     *
     * @param holder プレイヤーのクリップボード
     * @param location アップロードしたときのプレイヤーの位置
     * @return 基準点からの相対位置と向き
     */
    private fun spawnPositionOf(holder: ClipboardHolder, location: Location): SchematicSpawnPosition {
        val origin = holder.clipboard.origin
        val relative = Vector3.at(location.x, location.y, location.z).subtract(origin.toVector3())
        val transformed = if (holder.transform.isIdentity) relative else holder.transform.apply(relative)
        return SchematicSpawnPosition(transformed.x(), transformed.y(), transformed.z(), location.yaw, location.pitch)
    }

    /**
     * プレイヤーのセッションからクリップボードを取り出す (メインスレッドで呼ぶこと)
     *
     * @param player クリップボードを持つプレイヤー
     * @return クリップボード。セッションがない、またはクリップボードが空の場合は null
     */
    private fun clipboardHolderOrNull(player: Player): ClipboardHolder? {
        // get() は無ければセッションを作ってしまうため、既存のセッションだけを見る
        val session = WorldEdit.getInstance().sessionManager.getIfPresent(BukkitAdapter.adapt(player)) ?: return null
        return try {
            session.clipboard
        } catch (_: EmptyClipboardException) {
            null
        }
    }

    /**
     * Sponge schematic v3 の形式を取得する
     *
     * WorldEdit と FAWE で BuiltInClipboardFormat の定数が異なるため、両方が登録している別名で引く
     * (WorldEdit 7.4.5 / FAWE 2.16.0 で、この別名が Sponge schematic v3 の形式に対応することを確認済み)。
     *
     * @return Sponge schematic v3 の形式
     * @throws IOException 導入されている WorldEdit / FAWE がこの形式に対応していない場合
     */
    private fun spongeV3Format(): ClipboardFormat =
        ClipboardFormats.findByAlias(SPONGE_V3_ALIAS)
            ?: throw IOException("Clipboard format '$SPONGE_V3_ALIAS' is not available in the installed WorldEdit")

    companion object {
        /** Sponge schematic v3 の形式の別名 (WorldEdit 7.3 以降と FAWE で共通) */
        const val SPONGE_V3_ALIAS: String = "sponge.3"
    }
}
