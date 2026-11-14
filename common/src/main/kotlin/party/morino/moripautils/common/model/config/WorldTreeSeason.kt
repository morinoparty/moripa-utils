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
 * 世界樹の季節 (葉の見た目) の設定
 *
 * @property name 季節の名前 (コマンドの引数に使う)。英小文字 / 数字 / ハイフン / アンダースコアのみ
 * @property months この季節にする月 (1 から 12)
 * @property palette 葉として置くブロックと重み
 * @throws IllegalArgumentException name の形式が不正な場合、months に範囲外の値がある場合、palette が空の場合
 */
@Serializable
data class WorldTreeSeason(
    val name: String,
    val months: List<Int>,
    val palette: List<WeightedBlock>,
) {
    init {
        // コマンドの引数として入力するため、表記ゆれの起きない小文字の識別子に限定する
        require(NAME_PATTERN.matches(name)) { "worldTree season name must match ${NAME_PATTERN.pattern}, but was '$name'" }
        // 月は 1 から 12 の範囲でなければならない (重複や網羅は WorldTreeConfig でまとめて確認する)
        require(months.all { it in MONTH_RANGE }) { "worldTree season months must be in $MONTH_RANGE, but was $months ($name)" }
        // 置くブロックが無いと季節を反映できない
        require(palette.isNotEmpty()) { "worldTree season palette must not be empty ($name)" }
    }

    /** この季節が置くブロックの種類の id (状態を除いたもの。置き換えや数え上げの対象に使う) */
    val typeIds: Set<String>
        get() = palette.map { it.typeId }.toSet()

    companion object {
        /** name に使用できる文字のパターン */
        val NAME_PATTERN: Regex = Regex("^[a-z0-9_-]+$")

        /** 月として使用できる範囲 */
        val MONTH_RANGE: IntRange = 1..12
    }
}
