/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import party.morino.moripautils.common.model.config.StorageConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

/**
 * AWS SDK for Java v2 を使った S3 互換ストレージの実装
 *
 * SDK は JAR に同梱せず、Paper では MoripaUtilsLoader が実行時に解決する。
 * クライアントは最初の操作時に生成し、使わないまま再読み込みされた場合は何も生成しない。
 *
 * @property config 接続先と認証情報 (呼び出し側で [StorageConfig.isConfigured] を確認しておくこと)
 */
class S3ObjectStorage(
    private val config: StorageConfig,
) : ObjectStorage {
    /** S3 クライアント (最初の操作時に生成する) */
    private val clientDelegate = lazy { createClient() }

    /** 生成済み、または初回アクセスで生成される S3 クライアント */
    private val client: S3Client by clientDelegate

    override suspend fun put(key: String, content: ByteArray, contentType: String) {
        val request = PutObjectRequest.builder()
            .bucket(config.bucket)
            .key(key)
            .contentType(contentType)
            .build()
        // SDK の同期クライアントは呼び出しスレッドをブロックするため、I/O スレッドで実行する
        withContext(Dispatchers.IO) {
            try {
                client.putObject(request, RequestBody.fromBytes(content))
            } catch (e: SdkException) {
                // 機能側が SDK の例外型に依存しないよう包み直す (認証エラーや接続失敗など)
                throw ObjectStorageException("Failed to put object '$key' to bucket '${config.bucket}': ${e.message}", e)
            }
        }
    }

    override fun close() {
        // 一度も使っていなければクライアント自体が存在しないので、生成せずに終わる
        if (clientDelegate.isInitialized()) {
            client.close()
        }
    }

    /**
     * 設定から S3 クライアントを生成する
     *
     * @return 生成したクライアント
     */
    private fun createClient(): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(config.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKeyId, config.secretAccessKey)),
            )
            // Netty / Apache HTTP Client を実行時に解決しなくて済むよう、JDK 標準の URLConnection を使う
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .forcePathStyle(config.pathStyleAccess)
            // 新しい SDK は既定で CRC チェックサムを付けるが、R2 / MinIO などの S3 互換ストレージでは拒否されることがあるため、
            // API が必須とする場合だけ付ける
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        // エンドポイントが空なら region から決まる AWS S3 の既定エンドポイントを使う
        if (config.endpoint.isNotEmpty()) {
            builder.endpointOverride(URI.create(config.endpoint))
        }
        return builder.build()
    }
}
