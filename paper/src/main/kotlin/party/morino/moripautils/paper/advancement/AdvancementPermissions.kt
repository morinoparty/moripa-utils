/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper.advancement

/**
 * 進捗の復元機能で使う権限ノード (paper/build.gradle.kts の permissions と一致させること)
 */
object AdvancementPermissions {
    /** /mu advancement restore で HuskSync のスナップショットから進捗だけを復元する権限 */
    const val RESTORE: String = "moripautils.advancement.restore"
}
