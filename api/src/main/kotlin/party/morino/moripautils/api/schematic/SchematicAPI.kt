/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.api.schematic

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * schematic を S3 互換ストレージへアップロードする API
 *
 * schematic は Sponge schematic v3 (.schem) 形式で schematics/{id}/schematic.schem に保存し、id として UUID v7 を払い出す。
 * 同じディレクトリの info.json には、タイトル・アップロードした人・スポーン位置・範囲の大きさなどを保存する。
 * Java のプラグインからも使えるよう、結果は [CompletableFuture] で返す。
 * 失敗した場合は [SchematicUploadException] で例外的に完了する ([SchematicUploadException.reason] で理由が分かる)。
 * Future はメインスレッド以外で完了することがあるため、Bukkit API を使う後続処理はメインスレッドへ戻してから行うこと。
 * [uploadClipboard] はクリップボードの取得をメインスレッドで行うため、メインスレッドで Future を join / get して待たないこと (デッドロックする)。
 */
interface SchematicAPI {
    /**
     * プレイヤーの WorldEdit / FAWE のクリップボードを schematic としてアップロードする
     *
     * //rotate などで設定した変形はクリップボードに反映してから書き出す。呼び出し自体はどのスレッドから行ってもよい。
     * 呼び出した時点のプレイヤーの位置を、クリップボードの基準点からの相対位置としてスポーン位置に記録する。
     *
     * @param player クリップボードを持つプレイヤー (アップロードした人として記録する)
     * @param title タイトル (null または空白のみなら記録しない。前後の空白は取り除く)
     * @return アップロードした schematic の id (UUID v7)
     */
    fun uploadClipboard(player: Player, title: String?): CompletableFuture<UUID>

    /**
     * Sponge schematic v3 (.schem) のバイト列をそのままアップロードする
     *
     * Sponge schematic v3 でない内容 (v1 / v2 や gzip でないもの) は拒否する。WorldEdit が導入されていなくても使える。
     * 呼び出し自体はどのスレッドから行ってもよい。スポーン位置は基準点 (0, 0, 0、向き 0) として記録する。
     *
     * @param content Sponge schematic v3 のバイト列 (gzip 圧縮された NBT)
     * @param title タイトル (null または空白のみなら記録しない。前後の空白は取り除く)
     * @param uploaderName アップロードした人として記録する名前 (null なら記録しない)
     * @return アップロードした schematic の id (UUID v7)
     */
    fun uploadSchematic(content: ByteArray, title: String?, uploaderName: String?): CompletableFuture<UUID>
}
