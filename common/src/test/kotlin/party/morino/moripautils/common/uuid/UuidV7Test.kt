/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.common.uuid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UuidV7Test {
    @Test
    @DisplayName("Builds the RFC 9562 test vector")
    fun buildsRfcTestVector() {
        // RFC 9562 Appendix A.6 の例 (2022-02-22 19:22:22 UTC) と同じ値になる
        val uuid = UuidV7.generate(
            epochMillis = 0x017F22E279B0L,
            randA = 0xCC3L,
            randB = 0x18C4DC0C0C07398FL,
        )

        assertEquals("017f22e2-79b0-7cc3-98c4-dc0c0c07398f", uuid.toString())
    }

    @Test
    @DisplayName("Generated ids have version 7, RFC variant and the current time")
    fun generatesVersion7WithCurrentTime() {
        val before = System.currentTimeMillis()
        val uuid = UuidV7.generate()
        val after = System.currentTimeMillis()

        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
        // 先頭 48 bit はミリ秒単位の Unix 時刻
        val timestamp = uuid.mostSignificantBits ushr 16
        assertTrue(timestamp in before..after, "$timestamp should be in $before..$after")
    }
}
