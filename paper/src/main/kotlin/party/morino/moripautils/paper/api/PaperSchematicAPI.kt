/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.api

import com.github.shynixn.mccoroutine.bukkit.scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.bukkit.entity.Player
import party.morino.moripautils.api.schematic.SchematicAPI
import party.morino.moripautils.api.schematic.SchematicUploadException
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.model.schematic.SchematicUploadResult
import party.morino.moripautils.paper.schematic.SchematicUploader
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * [SchematicAPI] の Paper 向けの実装
 *
 * 再読み込みのたびに Koin コンテナが作り直されるため、アップロード役は呼び出しのたびにコンテナから取り出す。
 */
class PaperSchematicAPI : SchematicAPI {
    override fun uploadClipboard(player: Player, title: String?): CompletableFuture<UUID> = runUpload { uploader ->
        uploader.uploadClipboard(player, title)
    }

    override fun uploadSchematic(content: ByteArray, title: String?, uploaderName: String?): CompletableFuture<UUID> =
        runUpload { uploader ->
            uploader.uploadSchematic(content, title, uploaderName)
        }

    /**
     * プラグインのコルーチンスコープでアップロードを行い、結果を Future に変換する
     *
     * プラグインが無効化されるとスコープごとキャンセルされ、Future もキャンセル扱いで完了する。
     * プラグインのスコープの既定はメインスレッドだが、呼び出し元がメインスレッドで待っても書き出し以外が進むよう I/O スレッドで始める。
     *
     * @param block アップロード役を受け取って結果を返す処理
     * @return 成功時は id、失敗時は [SchematicUploadException] で完了する Future
     */
    private fun runUpload(block: suspend (SchematicUploader) -> SchematicUploadResult): CompletableFuture<UUID> {
        // プラグインが有効になっていない場合はコンテナが存在しない
        val koin = MoripaUtilsKoinContext.getOrNull()
            ?: return CompletableFuture.failedFuture(IllegalStateException("MoripaUtils is not enabled"))
        val plugin = koin.get<MoripaUtils>()
        val uploader = koin.get<SchematicUploader>()
        return plugin.scope.future(Dispatchers.IO) {
            when (val result = block(uploader)) {
                is SchematicUploadResult.Success -> result.id
                is SchematicUploadResult.Failure -> throw SchematicUploadException(result.reason, result.message)
            }
        }
    }
}
