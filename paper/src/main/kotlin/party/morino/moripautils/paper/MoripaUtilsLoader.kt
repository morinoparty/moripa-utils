/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.moripautils.paper

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.Exclusion
import org.eclipse.aether.repository.RemoteRepository
import party.morino.moripautils.common.BuildConstants

@Suppress("unused")
class MoripaUtilsLoader : PluginLoader {
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()
        // ビルドに使った Kotlin と同じバージョンの stdlib を解決する (gradle/libs.versions.toml の kotlin と連動)
        resolver.addDependency(
            Dependency(DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:${BuildConstants.KOTLIN_VERSION}"), null),
        )
        // S3 互換ストレージのクライアント (約 10MB あるため JAR に同梱せず、実行時に解決する)
        // HTTP クライアントは JDK 標準の URLConnection 実装を使うので、既定で付いてくる Netty / Apache 実装は除外する
        val unusedHttpClients = listOf(
            Exclusion("software.amazon.awssdk", "netty-nio-client", "*", "*"),
            Exclusion("software.amazon.awssdk", "apache-client", "*", "*"),
            Exclusion("software.amazon.awssdk", "apache5-client", "*", "*"),
        )
        AWS_SDK_ARTIFACTS.forEach { artifactId ->
            resolver.addDependency(
                Dependency(
                    DefaultArtifact("software.amazon.awssdk:$artifactId:${BuildConstants.AWS_SDK_VERSION}"),
                    null,
                    false,
                    unusedHttpClients,
                ),
            )
        }
        resolver.addRepository(
            RemoteRepository.Builder("paper", "default", "https://repo.papermc.io/repository/maven-public/").build(),
        )
        // AWS SDK は Maven Central にしかないため、Paper が推奨する Maven Central のミラーも使う
        resolver.addRepository(
            RemoteRepository.Builder("central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build(),
        )
        classpathBuilder.addLibrary(resolver)
    }

    companion object {
        /** 実行時に解決する AWS SDK のアーティファクト (gradle/libs.versions.toml の storage バンドルと揃える) */
        private val AWS_SDK_ARTIFACTS: List<String> = listOf("s3", "url-connection-client")
    }
}
