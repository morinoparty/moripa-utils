/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import xyz.jpenilla.resourcefactory.bukkit.Permission
import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory)
}

group = "party.morino"
version = project.version.toString()

dependencies {
    implementation(project(":common"))
    implementation(project(":api"))
    compileOnly(libs.paper.api)

    implementation(libs.bundles.commands.paper)

    implementation(libs.kotlinx.serialization.json)
    // 設定ファイル (config.conf) の HOCON 形式 (common と同じものを Paper 側のコードからも参照できるようにする)
    implementation(libs.bundles.config)
    // チケット機能の永続化 (Exposed + SQLite)。Paper 側でテーブル定義やリポジトリを書けるようにする
    implementation(libs.bundles.database)
    // MineAuth の公開 API。実行時は MineAuth プラグインのクラスを joinClasspath で参照するため同梱しない
    compileOnly(libs.mineauth.api)
    implementation(libs.bundles.coroutines.bukkit)

    // JARにバンドル
    implementation(libs.koin.core)
    // Prometheus クライアント (レジストリ / HTTP エクスポーター / JVM メトリクス)
    implementation(libs.bundles.prometheus)

    // テスト依存関係
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.junit.jupiter)
    testImplementation(libs.bundles.koin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.mock.bukkit)
    // スクレイプ結果をテキスト形式で検証するために使う
    testImplementation(libs.prometheus.textformats)
}

tasks {
    build {
        dependsOn("shadowJar")
    }
    shadowJar {
        // 他プラグインが同梱する Prometheus クライアントとクラスが衝突しないようにパッケージを移動する
        relocate("io.prometheus.metrics", "party.morino.moripautils.libs.io.prometheus.metrics")
        // MineAuth など同じサーバー上の他プラグインも Koin を使うため、GlobalContext などが共有されないよう移動する
        relocate("org.koin", "party.morino.moripautils.libs.org.koin")
        // Typesafe Config も他プラグインが同梱していることがあるため移動する
        relocate("com.typesafe.config", "party.morino.moripautils.libs.com.typesafe.config")
        // SQLite の JDBC ドライバー (org.xerial:sqlite-jdbc) は Paper 本体がサーバーのクラスパスに同梱しており、
        // プラグインのクラスローダーは親 (サーバー) を優先するため、同梱しても使われない。
        // 約 13MB のネイティブライブラリを JAR に含めないよう除外し、実行時はサーバー同梱のドライバーを使う。
        // (org.sqlite はネイティブライブラリのパスをパッケージ名から求めるため、relocate で新しい版を使うこともできない)
        dependencies {
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        }
        // Exposed の JDBC 接続自動登録 (META-INF/services) を JAR 内で結合して失わないようにする
        mergeServiceFiles()
        // 依存ライブラリのライセンスファイルが JAR 直下で重複しないよう除外する
        exclude("META-INF/LICENSE", "META-INF/NOTICE")
    }
    test {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    runServer {
        minecraftVersion("26.2")
    }
}

sourceSets.main {
    resourceFactory {
        paperPluginYaml {
            name = rootProject.name
            version = project.version.toString()
            website = "https://github.com/morinoparty/moripa-utils"
            main = "$group.moripautils.paper.MoripaUtils"
            bootstrapper = "$group.moripautils.paper.MoripaUtilsBootstrap"
            loader = "$group.moripautils.paper.MoripaUtilsLoader"
            apiVersion = "26.2"
            dependencies {
                // MineAuth は任意依存。先にロードし、joinClasspath で mineauth-api のクラスを参照できるようにする
                server("MineAuth", load = PaperPluginYaml.Load.BEFORE, required = false, joinClasspath = true)
            }
            permissions {
                // /ticket でお問い合わせを送信する権限 (全員に許可)
                register("moripautils.ticket.use") {
                    description = "Allows submitting tickets with /ticket"
                    default = Permission.Default.TRUE
                }
                // 新しいチケットやプレイヤーからのコメントの通知を受け取る権限
                register("moripautils.ticket.notify") {
                    description = "Receives notifications of new tickets and player comments"
                    default = Permission.Default.OP
                }
                // チケットの閲覧や対応を行うスタッフ向けの権限
                register("moripautils.ticket.staff") {
                    description = "Allows viewing and handling tickets"
                    default = Permission.Default.OP
                }
            }
        }
    }
}
