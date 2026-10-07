/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import party.morino.moripautils.common.model.ticket.TicketCloseReason
import party.morino.moripautils.common.model.ticket.TicketStatus

/**
 * チケットを保存する tickets テーブルの定義
 *
 * 列名を変更すると既存のデータベースと互換性がなくなるため、変更する場合は移行処理を用意すること。
 */
object TicketsTable : Table("tickets") {
    /** 通し番号 (カーソル方式のページングにも使う) */
    val id = long("id").autoIncrement()

    /** 送信元のサーバー (config.conf の server) */
    val serverId = varchar("server_id", SERVER_ID_LENGTH)

    /** 送信者の UUID (Exposed の uuid 列は kotlin.uuid.Uuid 型になるため、扱いやすい文字列で保存する) */
    val playerUuid = varchar("player_uuid", UUID_LENGTH).index()

    /** 送信時点のプレイヤー名 */
    val playerName = varchar("player_name", PLAYER_NAME_LENGTH)

    /** 送信時にプレイヤーがいたワールド名 */
    val world = varchar("world", WORLD_LENGTH)

    /** 送信時にプレイヤーがいたブロックの X 座標 */
    val blockX = integer("x")

    /** 送信時にプレイヤーがいたブロックの Y 座標 */
    val blockY = integer("y")

    /** 送信時にプレイヤーがいたブロックの Z 座標 */
    val blockZ = integer("z")

    /** カテゴリー id (config.conf の ticket.categories[].id) */
    val categoryId = varchar("category_id", CATEGORY_ID_LENGTH).index()

    /** 本文 */
    val content = text("content")

    /** 対応状況 (enum の名前で保存する) */
    val status = enumerationByName("status", STATUS_LENGTH, TicketStatus::class).index()

    /** クローズした理由 (enum の名前で保存する。オープン中は null) */
    val closeReason = enumerationByName("close_reason", STATUS_LENGTH, TicketCloseReason::class).nullable()

    /** 送信日時 */
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    /** server_id の最大長 */
    private const val SERVER_ID_LENGTH = 64

    /** UUID 文字列の長さ (ハイフン込み) */
    private const val UUID_LENGTH = 36

    /** プレイヤー名の最大長 (Java 版は 16 文字だが Bedrock 連携のプレフィックスなどに備えて余裕を持たせる) */
    private const val PLAYER_NAME_LENGTH = 64

    /** ワールド名の最大長 (Bukkit は長さを制限しないが、通常のワールド名には十分な長さにする) */
    private const val WORLD_LENGTH = 128

    /** カテゴリー id の最大長 */
    private const val CATEGORY_ID_LENGTH = 64

    /** 状態名の最大長 */
    private const val STATUS_LENGTH = 16
}
