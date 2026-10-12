/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // 外部プラグインが依存できるよう、JitPack で Maven 成果物として公開する
    `maven-publish`
}

group = "party.morino"
version = project.version.toString()

dependencies {
    compileOnly(libs.paper.api)
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlinx.serialization.json)
}

java {
    // IDE で KDoc を参照できるようソースも一緒に公開する
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // JitPack は公開されたモジュールが 1 つだけの場合、group / artifact を com.github.morinoparty:moripa-utils に読み替えて配布する
            artifactId = "api"
            from(components["java"])
        }
    }
}
