/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.api

import party.morino.moripautils.api.MoripaUtilsAPI
import party.morino.moripautils.api.schematic.SchematicAPI

/**
 * [MoripaUtilsAPI] の Paper 向けの実装
 *
 * 各機能の API は呼び出し時に Koin から依存を取り出すため、再読み込み後もこのインスタンスをそのまま使える。
 */
class PaperMoripaUtilsAPI : MoripaUtilsAPI {
    override val schematic: SchematicAPI = PaperSchematicAPI()
}
