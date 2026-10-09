/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.classloader

import java.io.IOException
import java.net.JarURLConnection
import java.net.URI
import java.util.jar.JarFile

/**
 * 稼働中にプラグインの JAR が削除・置き換えされても、JAR 内のリソースを読めるようにする
 *
 * Paper / Velocity はクラスを開いたままの JarFile から読むため、JAR を削除してもクラスは読み込める。
 * 一方、リソース (kotlin-reflect の .kotlin_builtins や META-INF/services など) は jar: URL 経由で読まれ、
 * JDK の JarURLConnection がキャッシュに無い JAR をパスから開き直すため、削除後は NoSuchFileException になる。
 * 起動時に一度 jar: URL で JAR を開いてキャッシュに載せておくと、以降の読み込みは開いたままの JarFile を使い回すため、
 * JAR を削除しても (Linux / macOS では開いているファイルは削除後も読めるため) 動き続ける。
 */
object PluginJarPin {
    /**
     * キャッシュに載せた JAR (JDK のキャッシュ自体も参照を持つが、固定していることを明示するため保持する)
     */
    @Volatile
    private var pinnedJar: JarFile? = null

    /**
     * [anchor] を含む JAR を開いてキャッシュに載せる
     *
     * JAR の削除より前 (ブートストラップやプラグインの初期化の最初) に呼ぶこと。
     * 2 回目以降の呼び出しや、JAR 以外 (テスト時のクラスディレクトリなど) から読み込まれている場合は何もしない。
     *
     * @param anchor プラグインの JAR に含まれるクラス
     * @return キャッシュに載せた JAR のパス。何もしなかった場合は null
     * @throws IOException JAR を開けなかった場合
     */
    @Synchronized
    fun pin(anchor: Class<*>): String? {
        if (pinnedJar != null) return null
        // クラスの読み込み元 (Paper / Velocity ではプラグインの JAR の file: URL)
        val location = anchor.protectionDomain?.codeSource?.location ?: return null
        if (location.protocol != "file" || !location.path.endsWith(".jar")) return null

        // URLClassLoader が内部で持つ JAR の読み込み役も、削除前に開かせておく (リソースの検索はこちらが行う)
        anchor.classLoader?.getResource(JarFile.MANIFEST_NAME)
        // リソースの URL (jar:file:...!/path) と同じキーでキャッシュされるよう、読み込み元の URL から組み立てる
        val connection = URI.create("jar:$location!/").toURL().openConnection() as JarURLConnection
        // キャッシュに載せることが目的なので、既定値に関係なく明示的に有効にする
        connection.useCaches = true
        pinnedJar = connection.jarFile
        return location.path
    }
}
