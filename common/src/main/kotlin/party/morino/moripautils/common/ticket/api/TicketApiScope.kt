/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.ticket.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 公開 API ([TicketAPIAdapter]) の処理を実行するコルーチンスコープ
 *
 * Paper / Velocity のどちらでも同じように扱えるよう、プラットフォームのスコープではなく専用のスコープを持つ。
 * データベースへのアクセスが中心のため I/O スレッドで実行し、1 件の失敗が他の呼び出しを巻き込まないよう SupervisorJob を使う。
 */
class TicketApiScope : AutoCloseable {
    /** API の処理を実行するスコープ */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 実行中の処理を取り消す (プラグインの無効化時や再読み込み時に呼ぶ)
     */
    override fun close() {
        scope.cancel()
    }
}
