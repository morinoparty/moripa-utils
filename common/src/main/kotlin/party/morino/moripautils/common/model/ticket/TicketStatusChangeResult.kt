/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.model.ticket

/**
 * チケットの状態の変更 (クローズ / 再オープン) の結果
 *
 * 失敗は例外ではなく値として返し、UI 側で理由ごとのメッセージを出し分けられるようにする。
 */
sealed interface TicketStatusChangeResult {
    /**
     * 変更に成功した
     *
     * @property change 変更の内容
     */
    data class Success(
        val change: TicketStatusChange,
    ) : TicketStatusChangeResult

    /** チケットが存在しない、または閲覧する権限がない (どちらかを区別させない) */
    data object TicketNotFound : TicketStatusChangeResult

    /** チケットは閲覧できるが、この操作を行う権限がない (本人による再オープンなど) */
    data object NotPermitted : TicketStatusChangeResult

    /** 既にクローズされている */
    data object AlreadyClosed : TicketStatusChangeResult

    /** 既にオープンしている */
    data object AlreadyOpen : TicketStatusChangeResult
}
