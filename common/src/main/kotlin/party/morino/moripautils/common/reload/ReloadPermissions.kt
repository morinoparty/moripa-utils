/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.reload

/**
 * 設定の再読み込みで使う権限ノード (Paper では paper/build.gradle.kts の permissions と一致させること)
 */
object ReloadPermissions {
    /** /mu reload (Paper) と /muv reload (Velocity) で config.conf を再読み込みする権限 */
    const val RELOAD: String = "moripautils.reload"
}
