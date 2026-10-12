/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.api

import party.morino.moripautils.api.schematic.SchematicAPI

/**
 * Paper 版の MoripaUtils が提供する公開 API
 *
 * Paper / Velocity で共通の [MoripaUtilsAPI] に加えて、Bukkit の API に依存する Paper 専用の機能を提供する。
 */
interface MoripaUtilsPaperAPI : MoripaUtilsAPI {
    /** schematic のアップロード (schematic 機能) の API */
    val schematic: SchematicAPI

    companion object {
        /**
         * Paper 版の公開 API を取得する
         *
         * @return Paper 版の MoripaUtils の公開 API
         * @throws IllegalStateException MoripaUtils がまだ有効化されていない場合や、Paper 以外 (Velocity) で呼んだ場合
         */
        fun getInstance(): MoripaUtilsPaperAPI {
            val api = MoripaUtilsAPI.getInstance()
            return checkNotNull(api as? MoripaUtilsPaperAPI) { "MoripaUtilsPaperAPI is only available on Paper" }
        }
    }
}
