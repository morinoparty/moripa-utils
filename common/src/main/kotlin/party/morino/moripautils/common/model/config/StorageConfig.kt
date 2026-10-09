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

/**
 * MoripaUtils 全体で共有するオブジェクトストレージ (S3 互換) の設定
 *
 * データベース ([DatabaseConfig]) とは別に、ファイルなどの大きなデータを保存する先として使う。
 * AWS S3 のほか、Cloudflare R2 / MinIO などの S3 互換ストレージにも [endpoint] を指定して接続できる。
 * [bucket] / [accessKeyId] / [secretAccessKey] のいずれかが空の場合は未設定とみなす ([isConfigured])。
 *
 * @property endpoint 接続先のエンドポイント URL。空文字の場合は [region] から決まる AWS S3 の既定エンドポイントを使う
 * @property region リージョン名 (例: ap-northeast-1。Cloudflare R2 では auto)
 * @property bucket 使用するバケット名。事前に作成しておくこと
 * @property accessKeyId 認証に使うアクセスキー ID
 * @property secretAccessKey 認証に使うシークレットアクセスキー
 * @property pathStyleAccess バケット名をホスト名ではなくパスに含める (path-style) かどうか。MinIO などで必要になる
 * @throws IllegalArgumentException region が空、または endpoint が http(s) の URL でない場合
 */
@Serializable
data class StorageConfig(
    val endpoint: String = "",
    val region: String = DEFAULT_REGION,
    val bucket: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val pathStyleAccess: Boolean = false,
) {
    init {
        // リージョンは署名 (SigV4) に必須なので、空のまま接続して認証エラーになるのを避ける
        require(region.isNotBlank()) { "storage.region must not be blank" }
        // スキームのない値は SDK 側で分かりにくいエラーになるため、読み込み時に弾く
        require(endpoint.isEmpty() || ENDPOINT_PATTERN.matches(endpoint)) {
            "storage.endpoint must start with http:// or https://, but was '$endpoint'"
        }
    }

    /** 接続に必要な値 (バケットと認証情報) がすべて設定されているかどうか */
    val isConfigured: Boolean
        get() = bucket.isNotBlank() && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()

    /** 秘密情報をログに出さないよう、シークレットアクセスキーを伏せた文字列にする */
    override fun toString(): String =
        "StorageConfig(endpoint=$endpoint, region=$region, bucket=$bucket, accessKeyId=$accessKeyId, " +
            "secretAccessKey=***, pathStyleAccess=$pathStyleAccess)"

    companion object {
        /** region の既定値 */
        const val DEFAULT_REGION: String = "us-east-1"

        /** endpoint に指定できる値のパターン (http / https のスキームを必須にする) */
        private val ENDPOINT_PATTERN: Regex = Regex("^https?://.+$")
    }
}
