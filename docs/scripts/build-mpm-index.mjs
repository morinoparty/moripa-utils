/**
 * mpm のリポジトリインデックス（index.json）を生成する。
 *
 * プラットフォーム（paper / velocity）ごとに、`docs/mpm/<platform>/plugins/*.json`（真実の源・コミット対象）と
 * `docs/mpm/<platform>/children.json`（子リポジトリへのリンク）を1枚のバンドルに束ね、
 * `docs/public/mpm/<platform>/index.json`（生成物・gitignore）として書き出す。
 *
 * このサイト（utils.plugin.morino.party）は mpm リポジトリグラフにおける morinoparty のノードである。
 * 中央リポジトリ（repo.mpm.nikomaru.dev）からは参照されておらず、利用者が config.json にリモートソースとして追加する。
 * mpm（クライアント）はここを起点に children をたどって MineAuth や MoripaFishing などの子リポジトリを読む。
 * mpm 自身の定義は中央リポジトリが持つため、ここには置かない。
 *
 * morinoparty のプラグインを追加するときは docs/mpm/<platform>/plugins/ に JSON を1つ足すだけでよい。
 * 子リポジトリを追加するときは docs/mpm/<platform>/children.json にリンクを1件足す。
 *
 * 定義は中央リポジトリの repo/public/paper/plugins/ と同じ形式のため、
 * 変換は中央の repo/scripts/build.ts と同じく `$schema` を落とすだけに留める
 * （downloadUrl / fileNameTemplate などのフィールドは削らない）。
 *
 * 形式の仕様: https://mpm.plugin.morino.party/docs/repository/graph
 */

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const docsDir = join(dirname(fileURLToPath(import.meta.url)), "..");

// インデックスを生成するプラットフォーム。それぞれ docs/mpm/<platform>/ を読み、public/mpm/<platform>/index.json に書き出す
const PLATFORMS = ["paper", "velocity"];

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
 */
const loadPlugins = (platform) => {
    const sourceDir = join(docsDir, "mpm", platform, "plugins");
    const files = readdirSync(sourceDir)
        .filter((file) => file.endsWith(".json"))
        .sort();

    const plugins = {};
    for (const fileName of files) {
        const name = fileName.replace(/\.json$/, "");
        const definition = JSON.parse(readFileSync(join(sourceDir, fileName), "utf-8"));

        const file = `${platform}/${fileName}`;
        // ファイル名と id の不一致はクライアント側で破棄されるため、ここで落とす
        if (definition.id !== name) {
            errors.push(`${file}: id (${definition.id}) がファイル名と一致しません`);
            continue;
        }
        if (!PLUGIN_NAME_PATTERN.test(name)) {
            errors.push(`${file}: プラグイン名に使えない文字が含まれています`);
            continue;
        }
        if (!Array.isArray(definition.repositories) || definition.repositories.length === 0) {
            errors.push(`${file}: repositories が空です`);
            continue;
        }
        // type と id が欠けた配布元はクライアントが解決できないため、ここで落とす
        const invalidRepository = definition.repositories.find(
            (repository) => !repository.type || !repository.id,
        );
        if (invalidRepository) {
            errors.push(`${file}: repositories に type または id が無いエントリがあります`);
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
 */
const loadChildren = (platform) => {
    const childrenFile = join(docsDir, "mpm", platform, "children.json");
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

// 全プラットフォームを先に読み込み、1つでもエラーがあれば何も書き出さずに終了する
const indexes = PLATFORMS.map((platform) => ({
    platform,
    plugins: loadPlugins(platform),
    children: loadChildren(platform),
}));

if (errors.length > 0) {
    for (const error of errors) {
        console.error(`✗ ${error}`);
    }
    process.exit(1);
}

const generated = new Date().toISOString();
for (const { platform, plugins, children } of indexes) {
    const index = {
        $schema: REPOSITORY_INDEX_SCHEMA,
        schemaVersion: 1,
        name: "morinoparty",
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
