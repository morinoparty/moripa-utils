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
 * 外部プラグイン向けの MoripaUtils の公開 API
 *
 * MoripaUtils の有効化後に [getInstance] で取得する。
 */
interface MoripaUtilsAPI {
    /** schematic のアップロード (schematic 機能) の API */
    val schematic: SchematicAPI

    companion object {
        private var instance: MoripaUtilsAPI? = null

        /**
         * 公開 API を取得する
         *
         * @return MoripaUtils の公開 API
         * @throws IllegalStateException MoripaUtils がまだ有効化されていない場合
         */
        fun getInstance(): MoripaUtilsAPI {
            return checkNotNull(instance) { "MoripaUtilsAPI is not initialized" }
        }

        /**
         * 公開 API の実装を設定する (MoripaUtils 本体が有効化時に呼ぶ)
         *
         * @param api 公開 API の実装
         */
        fun setInstance(api: MoripaUtilsAPI) {
            instance = api
        }
    }
}
