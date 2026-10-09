/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.schematic

import kotlinx.coroutines.CancellationException
import org.bukkit.entity.Player
import org.koin.core.component.inject
import party.morino.moripautils.api.schematic.SchematicUploadFailure
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.schematic.SchematicInfo
import party.morino.moripautils.common.model.schematic.SchematicSpawnPosition
import party.morino.moripautils.common.model.schematic.SchematicUploadRequest
import party.morino.moripautils.common.schematic.SchematicFormatReader
import party.morino.moripautils.common.schematic.SchematicUploadService
import party.morino.moripautils.common.schematic.SpongeSchematicV3Format
import party.morino.moripautils.paper.MoripaUtils
import party.morino.moripautils.paper.model.schematic.SchematicUploadResult
import party.morino.moripautils.paper.schematic.worldedit.WorldEditSchematicExporter
import java.util.logging.Level

/**
 * schematic のアップロードの入口 (/mu schematic upload と公開 API が共通で使う)
 *
 * WorldEdit の有無とストレージの設定を確認してから、書き出し ([WorldEditSchematicExporter]) と
 * アップロード ([SchematicUploadService]) を順に行い、結果を [SchematicUploadResult] にまとめる。
 * WorldEdit もストレージも任意のため、このクラス自体は常に Koin に登録しておく。
 */
class SchematicUploader : MoripaUtilsKoinComponent {
    private val plugin: MoripaUtils by inject()

    /**
     * プレイヤーのクリップボードをアップロードする
     *
     * @param player クリップボードを持つプレイヤー (アップロードした人として記録する)
     * @param title タイトル (null または空白のみなら記録しない)
     * @return アップロードの結果
     */
    suspend fun uploadClipboard(player: Player, title: String?): SchematicUploadResult {
        val normalizedTitle = normalizeTitle(title) ?: return invalidTitle()
        // WorldEdit の API クラスに触れる前に、Bukkit の API だけで存在を確認する
        if (!isWorldEditEnabled()) {
            return failure(SchematicUploadFailure.WORLDEDIT_UNAVAILABLE, "WorldEdit or FastAsyncWorldEdit is not enabled")
        }
        val service = serviceOrNull() ?: return storageNotConfigured()
        return catchingFailures {
            val exported = WorldEditSchematicExporter().exportClipboard(player)
                ?: return@catchingFailures failure(SchematicUploadFailure.EMPTY_CLIPBOARD, "Clipboard of ${player.name} is empty")
            // 書き出した内容から大きさを読む (WorldEdit が書いた Sponge schematic v3 なので必ず読めるはず)
            val worldSize = READER.readWorldSize(exported.content)
                ?: return@catchingFailures failure(SchematicUploadFailure.UPLOAD_FAILED, "Exported clipboard is not readable")
            val request = SchematicUploadRequest(
                format = READER.format,
                content = exported.content,
                worldSize = worldSize,
                title = normalizedTitle.value,
                uploaderName = player.name,
                uploaderUuid = player.uniqueId,
                spawnPosition = exported.spawnPosition,
            )
            upload(service, request)
        }
    }

    /**
     * Sponge schematic v3 のバイト列をアップロードする
     *
     * 形式の判定は WorldEdit を使わずに行うため、WorldEdit が導入されていなくても使える。
     * プレイヤーの位置が分からないため、スポーン位置は基準点 ([SchematicSpawnPosition.ORIGIN]) として記録する。
     *
     * @param content Sponge schematic v3 のバイト列
     * @param title タイトル (null または空白のみなら記録しない)
     * @param uploaderName アップロードした人として記録する名前 (null なら記録しない)
     * @return アップロードの結果
     */
    suspend fun uploadSchematic(content: ByteArray, title: String?, uploaderName: String?): SchematicUploadResult {
        val normalizedTitle = normalizeTitle(title) ?: return invalidTitle()
        val service = serviceOrNull() ?: return storageNotConfigured()
        // ストレージに不正なファイルが溜まらないよう、Sponge schematic v3 として読めるかを先に確かめる
        val worldSize = READER.readWorldSize(content)
            ?: return failure(SchematicUploadFailure.INVALID_SCHEMATIC, "Content is not a Sponge schematic v3")
        val request = SchematicUploadRequest(
            format = READER.format,
            content = content,
            worldSize = worldSize,
            title = normalizedTitle.value,
            uploaderName = uploaderName?.takeIf { it.isNotBlank() },
            uploaderUuid = null,
            spawnPosition = SchematicSpawnPosition.ORIGIN,
        )
        return catchingFailures { upload(service, request) }
    }

    /**
     * 書き出しやアップロードで発生した例外を失敗の結果に変換する
     *
     * 呼び出し元 (コマンドや他プラグイン) が必ず結果を受け取れるよう、キャンセル以外の例外はすべて捕捉する。
     * 互換性のない WorldEdit が導入されている場合のクラス解決の失敗 (LinkageError) は WorldEdit が使えないものとして扱う。
     *
     * @param block アップロードを行い結果を返す処理
     * @return 処理の結果、または例外を変換した失敗
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun catchingFailures(block: suspend () -> SchematicUploadResult): SchematicUploadResult = try {
        block()
    } catch (e: CancellationException) {
        // コルーチンのキャンセルは握りつぶさず伝播させる
        throw e
    } catch (e: LinkageError) {
        plugin.logger.warning("WorldEdit integration failed (incompatible WorldEdit API?): $e")
        failure(SchematicUploadFailure.WORLDEDIT_UNAVAILABLE, "Incompatible WorldEdit API: $e")
    } catch (e: Exception) {
        // ストレージへの保存の失敗 (ObjectStorageException) のほか、WorldEdit の書き出しや endpoint の解釈の失敗なども含む
        plugin.logger.log(Level.WARNING, "Failed to upload schematic", e)
        failure(SchematicUploadFailure.UPLOAD_FAILED, e.message ?: e.toString())
    }

    /**
     * schematic をアップロードして成功の結果を作る
     *
     * @param service アップロード役
     * @param request アップロードする schematic と情報
     * @return 成功の結果
     * @throws party.morino.moripautils.common.storage.ObjectStorageException アップロードに失敗した場合
     */
    private suspend fun upload(service: SchematicUploadService, request: SchematicUploadRequest): SchematicUploadResult {
        val id = service.upload(request)
        plugin.logger.info("Uploaded schematic $id (${request.content.size} bytes) by ${request.uploaderName ?: "unknown"}")
        return SchematicUploadResult.Success(id)
    }

    /**
     * タイトルの前後の空白を取り除き、長さを確かめる
     *
     * @param title 入力されたタイトル
     * @return 整えたタイトル (空なら値が null)。長すぎる場合は null
     */
    private fun normalizeTitle(title: String?): NormalizedTitle? {
        val trimmed = title?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.length > SchematicInfo.MAX_TITLE_LENGTH) return null
        return NormalizedTitle(trimmed)
    }

    /**
     * タイトルが長すぎることを表す失敗の結果を作る
     *
     * @return 失敗の結果
     */
    private fun invalidTitle(): SchematicUploadResult = failure(
        SchematicUploadFailure.INVALID_TITLE,
        "title must be at most ${SchematicInfo.MAX_TITLE_LENGTH} characters",
    )

    /**
     * アップロード役を取得する
     *
     * @return アップロード役。ストレージが設定されていない場合は Koin に登録されていないため null
     */
    private fun serviceOrNull(): SchematicUploadService? = getKoin().getOrNull<SchematicUploadService>()

    /**
     * WorldEdit または FAWE が有効かどうかを判定する
     *
     * @return どちらかが有効なら true
     */
    private fun isWorldEditEnabled(): Boolean = WORLDEDIT_PLUGIN_NAMES.any { name ->
        plugin.server.pluginManager.getPlugin(name)?.isEnabled == true
    }

    /**
     * ストレージが設定されていないことを表す失敗の結果を作る
     *
     * @return 失敗の結果
     */
    private fun storageNotConfigured(): SchematicUploadResult =
        failure(SchematicUploadFailure.STORAGE_NOT_CONFIGURED, "storage is not configured in config.conf")

    /**
     * 失敗の結果を作る
     *
     * @param reason 失敗した理由
     * @param message 詳細
     * @return 失敗の結果
     */
    private fun failure(reason: SchematicUploadFailure, message: String): SchematicUploadResult =
        SchematicUploadResult.Failure(reason, message)

    /**
     * 整えたタイトル (空のタイトルと「長すぎて不正」を区別するために包む)
     *
     * @property value タイトル。指定されなかった場合は null
     */
    @JvmInline
    private value class NormalizedTitle(val value: String?)

    companion object {
        /** アップロードする形式の読み取り役 (現在は Sponge schematic v3 のみ) */
        private val READER: SchematicFormatReader = SpongeSchematicV3Format

        /** WorldEdit の API を提供するプラグインの名前 (FAWE は FastAsyncWorldEdit という名前で動作する) */
        private val WORLDEDIT_PLUGIN_NAMES: List<String> = listOf("WorldEdit", "FastAsyncWorldEdit")
    }
}
