/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.model.reload

/**
 * 設定の再読み込み (/mu reload, /muv reload) の結果
 *
 * 失敗は例外ではなく値として返し、コマンド側で理由ごとのメッセージを出し分けられるようにする。
 */
sealed interface ReloadResult {
    /**
     * 再読み込みに成功した
     *
     * @property restartRequiredForCommands コマンドの登録状態が新しい設定と一致せず、反映にサーバーの再起動が必要な場合は true
     *   (コマンドはブートストラップ段階でしか登録できないため、無効だった機能を有効にしてもコマンドは増えない)
     */
    data class Success(
        val restartRequiredForCommands: Boolean = false,
    ) : ReloadResult

    /**
     * config.conf の読み込みに失敗した (稼働中の機能は古い設定のまま動き続ける)
     *
     * @property message 失敗の理由
     */
    data class InvalidConfig(
        val message: String,
    ) : ReloadResult

    /** 別の再読み込みが実行中 */
    data object InProgress : ReloadResult
}
