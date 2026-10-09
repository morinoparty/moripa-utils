/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    // kapt は kotlin-gradle-plugin に同梱されているため、ルートでバージョンを解決してから各モジュールで alias できるようにする
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spotless)
    id("dev.detekt") version "2.0.0-alpha.6"
}

val version: String by project
group = "party.morino"

buildscript {
    repositories {
        mavenCentral()
    }
}

allprojects {

    apply(plugin = "java")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
        maven("https://repo.codemc.io/repository/maven-public/")
        // WorldEdit の API
        maven("https://maven.enginehub.org/repo/")
    }

    dependencies {
        compileOnly(kotlin("stdlib"))
        compileOnly("org.jetbrains:annotations:26.1.0")
    }

    kotlin {
        jvmToolchain {
            (this).languageVersion.set(JavaLanguageVersion.of(25))
        }
        jvmToolchain(25)
    }

    tasks {
        register("hello") {
            doLast {
                println("I'm ${this.project.name}")
            }
        }
        test {
            useJUnitPlatform()
            testLogging {
                showStandardStreams = true
                events("passed", "skipped", "failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
        compileKotlin {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
            compilerOptions.javaParameters = true
            compilerOptions.languageVersion.set(KotlinVersion.KOTLIN_2_0)
        }
        compileTestKotlin {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
        }

        withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(25)
}

dependencies {
    dokka(project(":common"))
    dokka(project(":paper"))
    dokka(project(":velocity"))
    dokka(project(":api"))
}

dokka {
    pluginsConfiguration.html {
        footerMessage.set("No right reserved. This docs under CC0 1.0.")
    }
    dokkaPublications.html {
        outputDirectory.set(file("${project.rootDir}/docs/public/dokka"))
    }
}
spotless {
    // ktlint / detekt と同様、当面は非ゲート（`check`/`build` を失敗させない）。
    // 開発者が任意に `./gradlew spotlessApply`（一括付与・更新）/ `spotlessCheck`（検証）を
    // 実行する運用とする（Taskfile の `task license` / `task license:check` も同じもの）。
    isEnforceCheck = false

    // ライセンスヘッダーは config/spotless/license-header.kt に一元管理し、$YEAR トークンで年を表す。
    // updateYearWithLatest により、既存の年を「開始年-現在年」の範囲へ更新する
    // （例: 2023 → 2023-2026）。新規ファイルは現在年のみ。全ファイルを対象にするため ratchet は使わない。
    val licenseHeader = rootProject.file("config/spotless/license-header.kt")
    kotlin {
        target("api/src/**/*.kt", "common/src/**/*.kt", "paper/src/**/*.kt", "velocity/src/**/*.kt")
        licenseHeaderFile(licenseHeader).updateYearWithLatest(true)
    }
    kotlinGradle {
        target("*.gradle.kts", "api/*.gradle.kts", "common/*.gradle.kts", "paper/*.gradle.kts", "velocity/*.gradle.kts")
        // .gradle.kts の最初の非ヘッダー行（build: import / settings: pluginManagement 等）を区切りとする。
        licenseHeaderFile(
            licenseHeader,
            "(import|plugins|pluginManagement|dependencyResolutionManagement|rootProject|@file)",
        ).updateYearWithLatest(true)
    }
}

detekt {
    source.setFrom(
        "api/src/main/java",
        "api/src/main/kotlin",
        "common/src/main/java",
        "common/src/main/kotlin",
        "paper/src/main/java",
        "paper/src/main/kotlin",
        "velocity/src/main/java",
        "velocity/src/main/kotlin",
    )
    parallel = true
    buildUponDefaultConfig = true
    allRules = true
    baseline = file("./detekt-baseline.xml")
    disableDefaultRuleSets = false
    debug = false
    ignoreFailures = true
}
