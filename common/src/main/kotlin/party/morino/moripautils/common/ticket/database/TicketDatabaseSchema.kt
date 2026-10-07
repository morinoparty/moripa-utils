/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.ticket.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.koin.core.component.inject
import party.morino.moripautils.common.database.MoripaUtilsDatabase
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent

/**
 * ticket 機能のテーブル (tickets / ticket_comments) を 1 回だけ作成する
 *
 * ticket_comments は tickets を参照するため、どちらのリポジトリが先に使われても両方を同時に作成する。
 * データベースの接続ごとに作り直せるよう、Koin のシングルトンとして登録して使う。
 */
class TicketDatabaseSchema : MoripaUtilsKoinComponent {
    private val database: MoripaUtilsDatabase by inject()

    /** テーブルを作成済みかどうか (複数スレッドから読まれるため volatile にする) */
    @Volatile
    private var ready = false

    /** 初回の操作が並行しても、テーブル作成を 1 回だけ行うためのロック */
    private val mutex = Mutex()

    /**
     * テーブルが無ければ作成する
     *
     * 本来の処理と同じトランザクションで作成すると、その処理が失敗したときに SQLite では作成ごとロールバックされ、
     * フラグだけが残ってしまう。そのため別のトランザクションで作成し、コミットに成功してからフラグを立てる。
     */
    suspend fun ensureCreated() {
        // 作成済みならロックを取らずに抜ける
        if (ready) return
        mutex.withLock {
            // ロック待ちの間に他のコルーチンが作成を終えている場合がある
            if (!ready) {
                // 参照先 (tickets) が先に作られるよう、Exposed に依存関係の順で作成させる
                database.query { SchemaUtils.create(TicketsTable, TicketCommentsTable) }
                ready = true
            }
        }
    }
}
