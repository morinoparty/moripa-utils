/**
 * mpm のリポジトリインデックス（index.json）を生成する。
 *
 * プラットフォーム（paper / velocity）ごとに、`docs/mpm/<platform>/plugins/*.json`（真実の源・コミット対象）と
 * `docs/mpm/<platform>/children.json`（子リポジトリへのリンク・任意）を1枚のバンドルに束ね、
 * `docs/public/mpm/<platform>/index.json`（生成物・gitignore）として書き出す。
 * Paper と Velocity では配布する JAR が異なるため、mpm の中央リポジトリと同じくカタログを分けて配信する。
 *
 * このサイト（utils.plugin.morino.party）は mpm リポジトリグラフにおける morinoparty のノードである。
 * 中央リポジトリ（repo.mpm.nikomaru.dev）からは参照されておらず、利用者が config.json にリモートソースとして追加する。
 * mpm（クライアント）はここを起点に children をたどって MineAuth や MoripaFishing などの子リポジトリを読む。
 * mpm 自身の定義は中央リポジトリが持つため、ここには置かない。
 *
 * morinoparty のプラグインを追加するときは docs/mpm/<platform>/plugins/ に JSON を1つ足すだけでよい。
 * 子リポジトリを追加するときは docs/mpm/<platform>/children.json にリンクを1件足す。
 *
 * 定義は中央リポジトリのプラグイン定義と同じ形式のため、
 * 変換は中央の repo/scripts/build.ts と同じく `$schema` を落とすだけに留める
 * （downloadUrl / fileNameTemplate などのフィールドは削らない）。
 *
 * 形式の仕様: https://mpm.plugin.morino.party/docs/repository/graph
 */

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const docsDir = join(dirname(fileURLToPath(import.meta.url)), "..");

// 生成するカタログ。name はインデックスの名前（中央リポジトリの Velocity カタログと同じく接尾辞で区別する）
const PLATFORMS = [
    { platform: "paper", name: "morinoparty" },
    { platform: "velocity", name: "morinoparty-velocity" },
];

// 中央リポジトリの build.ts が配信している repository-index スキーマのURL
const REPOSITORY_INDEX_SCHEMA = "https://repo.mpm.nikomaru.dev/schema/repository-index/v1.json";

// プラグイン名として mpm が受け付ける文字（中央の repository-index.ts と同じ規則）
const PLUGIN_NAME_PATTERN = /^[A-Za-z0-9_-]+$/;

// 子リポジトリの index は https 限定・.json 拡張子必須（中央の RepositoryLinkSchema と同じ規則）
const CHILD_INDEX_PATTERN = /^https:\/\/.+\.json$/;

// 子リンクの allowedSources は "type:id" 形式（中央の RepositoryLinkSchema と同じ規則）
const ALLOWED_SOURCE_PATTERN = /^[a-z]+:[^\s]+$/;

const errors = [];

/**
 * docs/mpm/<platform>/plugins/*.json を読み込み、検証してプラグイン名 -> 定義のマップにする。
 * 公開バンドルには `$schema` を含めない（手元のIDE補完用でクライアントには不要）。
 *
 * @param platform プラットフォーム名（エラーメッセージの接頭辞にも使う）
 * @param sourceDir プラグイン定義のディレクトリ
 */
const loadPlugins = (platform, sourceDir) => {
    const files = readdirSync(sourceDir)
        .filter((file) => file.endsWith(".json"))
        .sort();

    const plugins = {};
    for (const file of files) {
        const name = file.replace(/\.json$/, "");
        const definition = JSON.parse(readFileSync(join(sourceDir, file), "utf-8"));

        // ファイル名と id の不一致はクライアント側で破棄されるため、ここで落とす
        if (definition.id !== name) {
            errors.push(`${platform}/plugins/${file}: id (${definition.id}) がファイル名と一致しません`);
            continue;
        }
        if (!PLUGIN_NAME_PATTERN.test(name)) {
            errors.push(`${platform}/plugins/${file}: プラグイン名に使えない文字が含まれています`);
            continue;
        }
        if (!Array.isArray(definition.repositories) || definition.repositories.length === 0) {
            errors.push(`${platform}/plugins/${file}: repositories が空です`);
            continue;
        }
        // type と id が欠けた配布元はクライアントが解決できないため、ここで落とす
        const invalidRepository = definition.repositories.find(
            (repository) => !repository.type || !repository.id,
        );
        if (invalidRepository) {
            errors.push(`${platform}/plugins/${file}: repositories に type または id が無いエントリがあります`);
            continue;
        }

        const { $schema, ...rest } = definition;
        plugins[name] = rest;
    }
    return plugins;
};

/**
 * docs/mpm/<platform>/children.json（子リポジトリへのリンク一覧）を読み込んで検証する。
 * ファイルが無ければ子なしとして扱う。
 *
 * @param platform プラットフォーム名（エラーメッセージの接頭辞にも使う）
 * @param childrenFile children.json のパス
 */
const loadChildren = (platform, childrenFile) => {
    if (!existsSync(childrenFile)) return [];
    const children = JSON.parse(readFileSync(childrenFile, "utf-8"));
    if (!Array.isArray(children)) {
        errors.push(`${platform}/children.json: 配列である必要があります`);
        return [];
    }

    // 同じ index を2回書くのは誤りとみなす（中央の build.ts と同じ扱い）
    const seen = new Set();
    for (const link of children) {
        if (typeof link?.index !== "string" || !CHILD_INDEX_PATTERN.test(link.index)) {
            errors.push(`${platform}/children.json: index は https で始まり .json で終わるURLである必要があります (${link?.index})`);
            continue;
        }
        if (seen.has(link.index)) {
            errors.push(`${platform}/children.json: 重複したリンクがあります (${link.index})`);
        }
        seen.add(link.index);
        // allowedSources は任意。指定した場合だけ形式を検証する
        if (link.allowedSources !== undefined) {
            const valid =
                Array.isArray(link.allowedSources) &&
                link.allowedSources.every(
                    (source) => typeof source === "string" && ALLOWED_SOURCE_PATTERN.test(source),
                );
            if (!valid) {
                errors.push(`${platform}/children.json: allowedSources は "type:id" 形式の文字列配列である必要があります (${link.index})`);
            }
        }
    }
    return children;
};

// すべてのプラットフォームを検証してから書き出す（一部だけ更新された状態で公開しないため）
const indexes = PLATFORMS.map(({ platform, name }) => {
    const platformDir = join(docsDir, "mpm", platform);
    return {
        platform,
        name,
        plugins: loadPlugins(platform, join(platformDir, "plugins")),
        children: loadChildren(platform, join(platformDir, "children.json")),
    };
});

if (errors.length > 0) {
    for (const error of errors) {
        console.error(`✗ ${error}`);
    }
    process.exit(1);
}

const generated = new Date().toISOString();
for (const { platform, name, plugins, children } of indexes) {
    const index = {
        $schema: REPOSITORY_INDEX_SCHEMA,
        schemaVersion: 1,
        name,
        generated,
        plugins,
        children,
    };

    const outputFile = join(docsDir, "public", "mpm", platform, "index.json");
    mkdirSync(dirname(outputFile), { recursive: true });
    writeFileSync(outputFile, `${JSON.stringify(index, null, 2)}\n`);

    console.log(
        `✓ Generated mpm ${platform} index with ${Object.keys(plugins).length} plugins and ${children.length} children`,
    );
}
