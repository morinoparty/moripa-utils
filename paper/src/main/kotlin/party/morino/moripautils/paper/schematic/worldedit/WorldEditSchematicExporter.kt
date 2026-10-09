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
import com.sk89q.worldedit.session.ClipboardHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.koin.core.component.inject
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.paper.MoripaUtils
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
     * プレイヤーのクリップボードを Sponge schematic v3 のバイト列にする
     *
     * セッションの参照はメインスレッドで、変形の反映と書き出しは重いため I/O スレッドで行う。
     *
     * @param player クリップボードを持つプレイヤー
     * @return schematic のバイト列。クリップボードが空の場合は null
     * @throws IOException 変形の反映や書き出しに失敗した場合、Sponge schematic v3 の形式が見つからない場合
     */
    suspend fun exportClipboard(player: Player): ByteArray? {
        val holder = withContext(plugin.minecraftDispatcher) { clipboardHolderOrNull(player) } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                // //rotate や //flip の変形はホルダーに保持されているだけなので、書き出す前にクリップボードへ反映する
                val transform = holder.transform
                val clipboard = if (transform.isIdentity) holder.clipboard else holder.clipboard.transform(transform)
                val output = ByteArrayOutputStream()
                // ライターを閉じたときに gzip が書き切られるため、use を抜けてからバイト列を取り出す
                spongeV3Format().getWriter(output).use { writer -> writer.write(clipboard) }
                output.toByteArray()
            } catch (e: WorldEditException) {
                // 呼び出し側が WorldEdit の例外型に触れなくて済むよう包み直す
                throw IOException("Failed to transform clipboard: ${e.message}", e)
            }
        }
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
