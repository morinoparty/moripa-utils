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
    // ticket の公開 API (TicketService / Exposed) が使うため、kotlinx-coroutines も同梱する
    implementation(libs.kotlinx.coroutines.core)
    compileOnly(libs.bundles.coroutines.velocity)
    // ticket の公開 API が共有データベース (MySQL) に接続するための JDBC ドライバー (Velocity 本体には同梱されていない)
    implementation(libs.mysql.connector)

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
            // Velocity の ticket の公開 API は MySQL にだけ接続するため、SQLite (ネイティブ込みで十数 MB) は同梱しない
            // (Exposed と、Exposed が依存する kotlin-reflect は同梱する)
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
