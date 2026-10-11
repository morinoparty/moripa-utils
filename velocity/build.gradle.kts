/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // Kotlin ソースの @Plugin を Velocity のアノテーションプロセッサで処理し velocity-plugin.json を生成する
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.shadow)
}

group = "party.morino"
version = project.version.toString()

dependencies {
    implementation(project(":common"))
    implementation(project(":api"))
    compileOnly(libs.velocity.api)
    // annotationProcessor は Java ソースにしか効かないため、Kotlin では kapt を使う
    kapt(libs.velocity.api)

    implementation(libs.bundles.commands.velocity)

    // compileOnly
    // Kotlin の標準ライブラリと kotlinx-serialization は JAR に同梱する
    // (他のプラグインから借りると、同名のクラスが別々のクラスローダーから読み込まれて LinkageError になるため)
    implementation(libs.kotlinx.serialization.json)
    implementation(kotlin("stdlib-jdk8"))
    compileOnly(libs.bundles.coroutines.velocity)

    // JARにバンドル
    implementation(libs.koin.core)
    // 設定ファイル (config.conf) の HOCON 形式
    implementation(libs.bundles.config)
    // Prometheus クライアント (レジストリ / HTTP エクスポーター / JVM メトリクス)
    implementation(libs.bundles.prometheus)

    // テスト依存関係
    testImplementation(libs.bundles.junit.jupiter)
    // compileOnly はテストのクラスパスに乗らないため、テストでは Velocity API を明示的に追加する
    testImplementation(libs.velocity.api)
    // スクレイプ結果をテキスト形式で検証するために使う
    testImplementation(libs.prometheus.textformats)
    testImplementation(libs.bundles.koin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.bundles.coroutines.velocity)
    testImplementation(libs.koin.core)
    testImplementation(kotlin("stdlib-jdk8"))
}

tasks {
    build {
        dependsOn("shadowJar")
    }
    shadowJar {
        // 他プラグインが同梱する Prometheus クライアントとクラスが衝突しないようにパッケージを移動する
        relocate("io.prometheus.metrics", "party.morino.moripautils.libs.io.prometheus.metrics")
        // 同じプロキシ上の他プラグインも Koin を使うため、GlobalContext などが共有されないよう移動する
        relocate("org.koin", "party.morino.moripautils.libs.org.koin")
        // Typesafe Config も他プラグインが同梱していることがあるため移動する
        relocate("com.typesafe.config", "party.morino.moripautils.libs.com.typesafe.config")
        mergeServiceFiles()
        // 依存ライブラリのライセンスファイルが JAR 直下で重複しないよう除外する
        exclude("META-INF/LICENSE", "META-INF/NOTICE")
        dependencies {
            // kotlin-reflect は Velocity 版のコードでは使わないため同梱しない
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-bom:.*"))
            // kotlinx.serialization の core / json は実行環境が提供するが、HOCON フォーマットは同梱する必要があるため除外しない
            // チケット機能は Paper 専用で Velocity では使わないため、Exposed / SQLite (ネイティブ込みで十数 MB) は同梱しない
            exclude(dependency("org.jetbrains.exposed:.*:.*"))
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        }
    }
    test {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
