/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.uuid

import java.security.SecureRandom
import java.util.UUID

/**
 * RFC 9562 の UUID version 7 を生成する
 *
 * 先頭 48 bit がミリ秒単位の Unix 時刻、残りが乱数のため、id の並びがおおよそ生成順になり、id から生成時刻も分かる。
 * 同じミリ秒内の順序は保証しないが、74 bit の乱数部で一意性は保たれる。
 * (Kotlin 標準ライブラリの Uuid.generateV7 は Kotlin 2.3 以降の API で、このプロジェクトの languageVersion 2.0 では使えないため自前で実装する)
 */
object UuidV7 {
    /** 乱数部の生成に使う暗号論的乱数 (id の推測を難しくする) */
    private val random = SecureRandom()

    /** バージョン (7) を置く位置のマスク (上位 64 bit の 12〜15 bit 目) */
    private const val VERSION_MASK: Long = 0xF000L

    /** バージョン 7 を表すビット */
    private const val VERSION_BITS: Long = 0x7000L

    /** 時刻を置く上位 64 bit 内のシフト量 (下位 16 bit はバージョンと乱数) */
    private const val TIMESTAMP_SHIFT: Int = 16

    /** 時刻部の幅 (48 bit) を表すマスク */
    private const val TIMESTAMP_MASK: Long = 0xFFFF_FFFF_FFFFL

    /** 上位 64 bit のうち乱数を置く 12 bit (rand_a) のマスク */
    private const val RAND_A_MASK: Long = 0x0FFFL

    /** バリアントを置く位置のマスク (下位 64 bit の最上位 2 bit) */
    private const val VARIANT_MASK: Long = -0x4000_0000_0000_0000L

    /** RFC 9562 のバリアント (0b10) を表すビット */
    private const val VARIANT_BITS: Long = Long.MIN_VALUE

    /**
     * 現在時刻から UUID v7 を生成する
     *
     * @return 生成した UUID
     */
    fun generate(): UUID = generate(System.currentTimeMillis(), random.nextLong(), random.nextLong())

    /**
     * 時刻と乱数から UUID v7 を組み立てる (テストで結果を固定できるよう、乱数を外から渡せる純粋関数にしている)
     *
     * @param epochMillis ミリ秒単位の Unix 時刻 (下位 48 bit だけを使う)
     * @param randA 上位 64 bit に使う乱数 (下位 12 bit だけを使う)
     * @param randB 下位 64 bit に使う乱数 (下位 62 bit だけを使う)
     * @return 組み立てた UUID
     */
    fun generate(epochMillis: Long, randA: Long, randB: Long): UUID {
        // 上位 64 bit: 時刻 48 bit | バージョン 4 bit | 乱数 12 bit
        val mostSigBits = ((epochMillis and TIMESTAMP_MASK) shl TIMESTAMP_SHIFT) or
            (VERSION_BITS and VERSION_MASK) or
            (randA and RAND_A_MASK)
        // 下位 64 bit: バリアント 2 bit | 乱数 62 bit
        val leastSigBits = (randB and VARIANT_MASK.inv()) or VARIANT_BITS
        return UUID(mostSigBits, leastSigBits)
    }
}
