/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper

import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter
import io.prometheus.metrics.model.registry.PrometheusRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.component.get
import party.morino.moripautils.common.di.MoripaUtilsKoinComponent
import party.morino.moripautils.common.model.reload.ReloadResult
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * /mu reload (MoripaUtils.reload) で各機能が新しいコンテナで起動し直されることを検証する
 */
@ExtendWith(MoripaUtilsTest::class)
class ReloadTest : MoripaUtilsKoinComponent {

    @Test
    @DisplayName("Reload recreates the registry without duplicating listeners")
    fun reloadRecreatesRegistryWithoutDuplicatingListeners() {
        // 再読み込みでコンテナが作り直されるため、by inject() ではなくその都度取得する
        val oldRegistry = get<PrometheusRegistry>()

        val result = runBlocking { MoripaUtilsTest.plugin.reload() }

        // MockBukkit はブートストラッパーを経由せず /ticket が未登録なので、再起動の要否は検証しない
        assertIs<ReloadResult.Success>(result)
        val newRegistry = get<PrometheusRegistry>()
        assertNotSame(oldRegistry, newRegistry)

        // 古いリスナーが残っていると 1 回の参加が 2 回数えられる
        MoripaUtilsTest.server.addPlayer()
        val text = PrometheusTextFormatWriter.create().toDebugString(newRegistry.scrape())
        assertTrue(text.contains("minecraft_player_joins_total 1.0"), text)
    }
}
