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
 * チケットをクローズした理由
 *
 * データベースには名前 ([name]) で保存するため、既存の値の名前は変更しないこと。
 *
 * @property id コマンド引数 (/ticket close --reason-type) や HTTP API で使う識別子
 */
enum class TicketCloseReason(
    val id: String,
) {
    /** 対応が完了した */
    DONE("done"),

    /** 対応しないことにした */
    NOT_PLANNED("not-planned"),

    /** 他のチケットと重複している */
    DUPLICATE("duplicate"),
    ;

    companion object {
        /** 理由を指定しなかったときに使う理由 */
        val DEFAULT: TicketCloseReason = DONE

        /**
         * 識別子から理由を引き当てる (大文字小文字は区別しない)
         *
         * @param id コマンド引数などで指定された識別子
         * @return 対応する理由、見つからなければ null
         */
        fun fromId(id: String): TicketCloseReason? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
