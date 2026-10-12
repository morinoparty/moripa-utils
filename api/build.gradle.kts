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
    // morinoparty の他のプラグインが依存できるよう、GitHub Packages に Maven 成果物として公開する
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
            // party.morino:api では何の API か分からないため、プロジェクト名を付ける
            artifactId = "moripa-utils-api"
            from(components["java"])
        }
    }
    repositories {
        // リリース時に GitHub Actions (upload.yml) から publish する。認証には GITHUB_TOKEN を使う
        maven("https://maven.pkg.github.com/morinoparty/moripa-utils") {
            name = "GitHubPackages"
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
