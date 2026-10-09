/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.storage

/**
 * 各機能が共有するオブジェクトストレージ
 *
 * 機能側が S3 の SDK に依存しないよう、必要な操作だけをこのインターフェースに切り出す。
 * 再読み込みやプラグインの無効化時には [close] で接続を解放すること。
 */
interface ObjectStorage : AutoCloseable {
    /**
     * オブジェクトを保存する (同じキーが既にあれば上書きする)
     *
     * 呼び出し元のスレッドを止めないよう、通信は I/O スレッドで行う。
     *
     * @param key 保存先のキー (例: schematics/xxxx.schem)
     * @param content 保存する内容
     * @param contentType 内容の MIME タイプ
     * @throws ObjectStorageException 保存に失敗した場合
     */
    suspend fun put(key: String, content: ByteArray, contentType: String)
}
