/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.schematic.command

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotation.specifier.Greedy
import party.morino.moripautils.api.schematic.SchematicUploadFailure
import party.morino.moripautils.common.model.schematic.SchematicInfo
import party.morino.moripautils.common.di.MoripaUtilsKoinContext
import party.morino.moripautils.paper.model.schematic.SchematicUploadResult
import party.morino.moripautils.paper.schematic.SchematicPermissions
import party.morino.moripautils.paper.schematic.SchematicUploader

/**
 * /mu schematic upload コマンド (WorldEdit のクリップボードを S3 互換ストレージへアップロードする)
 *
 * ブートストラップ段階で登録するため、生成時点では Koin コンテナがまだ存在しない。
 * また再読み込みのたびにコンテナが作り直されるため、アップロード役はコマンドの実行時にコンテナから取り出す。
 */
@Suppress("UnstableApiUsage")
class SchematicCommand {
    /**
     * 実行したプレイヤーのクリップボードをアップロードし、id を表示する
     *
     * 実行した時点のプレイヤーの位置を、schematic を使うときのスポーン位置として記録する。
     *
     * @param source コマンドの実行元 (プレイヤーのみ)
     * @param title タイトル (省略可。空白を含めて残りの引数すべてを使う)
     */
    @Command("mu schematic upload [title]")
    @Permission(SchematicPermissions.UPLOAD)
    @CommandDescription("WorldEdit のクリップボードを schematic としてアップロードします")
    suspend fun upload(
        source: CommandSourceStack,
        @Argument("title") @Greedy title: String?,
    ) {
        val sender = source.sender
        // クリップボードはプレイヤーごとのセッションにあるため、コンソールからは実行できない
        val player = sender as? Player
        if (player == null) {
            sender.sendRichMessage("<red>このコマンドはプレイヤーのみ実行できます。")
            return
        }
        // プラグインの有効化に失敗した場合はコンテナが存在しない
        val uploader = MoripaUtilsKoinContext.getOrNull()?.getOrNull<SchematicUploader>()
        if (uploader == null) {
            player.sendRichMessage("<red>MoripaUtils が有効になっていないためアップロードできません。")
            return
        }

        // 大きなクリップボードは書き出しと送信に時間がかかるため、先に受け付けたことを伝える
        player.sendRichMessage("<gray>クリップボードをアップロードしています...")
        when (val result = uploader.uploadClipboard(player, title)) {
            // UUID には MiniMessage のタグとして解釈される文字が含まれないため、そのまま埋め込む
            is SchematicUploadResult.Success -> player.sendRichMessage(
                "<green>アップロードしました。ID: " +
                    "<click:copy_to_clipboard:'${result.id}'><hover:show_text:'クリックでコピー'>" +
                    "<yellow>${result.id}</yellow></hover></click>",
            )
            is SchematicUploadResult.Failure -> player.sendRichMessage("<red>${failureMessage(result.reason)}")
        }
    }

    /**
     * 失敗の理由をプレイヤー向けの文章にする
     *
     * @param reason 失敗した理由
     * @return 表示する文章
     */
    private fun failureMessage(reason: SchematicUploadFailure): String = when (reason) {
        SchematicUploadFailure.WORLDEDIT_UNAVAILABLE -> "WorldEdit (または FAWE) が利用できないためアップロードできません。"
        SchematicUploadFailure.STORAGE_NOT_CONFIGURED -> "アップロード先のストレージが設定されていません。"
        SchematicUploadFailure.EMPTY_CLIPBOARD -> "クリップボードが空です。//copy などで範囲をコピーしてから実行してください。"
        SchematicUploadFailure.INVALID_SCHEMATIC -> "schematic の形式が不正です。"
        SchematicUploadFailure.INVALID_TITLE -> "タイトルは ${SchematicInfo.MAX_TITLE_LENGTH} 文字以内で指定してください。"
        SchematicUploadFailure.UPLOAD_FAILED -> "アップロードに失敗しました。詳細はサーバーのログを確認してください。"
    }
}
