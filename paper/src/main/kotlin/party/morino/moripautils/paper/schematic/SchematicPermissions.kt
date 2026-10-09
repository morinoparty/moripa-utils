/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.paper.schematic

/**
 * schematic 機能で使う権限ノード (paper/build.gradle.kts の permissions と一致させること)
 */
object SchematicPermissions {
    /** /mu schematic upload でクリップボードをアップロードする権限 */
    const val UPLOAD: String = "moripautils.schematic.upload"
}
