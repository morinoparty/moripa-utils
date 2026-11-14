# moripa-utils

morinoparty の Minecraft サーバー向けユーティリティプラグイン「MoripaUtils」です。
運営に必要な機能を機能 (feature) 単位でまとめて提供します。
Paper / Velocity 両対応のマルチモジュール構成になっています。

| 機能 | 内容 |
|------|------|
| observability | プレイヤー人数やサーバーの TPS などのメトリクスを Prometheus 形式で公開し、Grafana で可視化する |
| ticket | `/ticket` コマンドから運営への問い合わせ (チケット) を送信できる UI (MineAuth 導入時は HTTP API から自分のチケットを参照可能) |
| world tree | ロビーの世界樹の葉を、サーバーの起動時に月に応じた季節のブロックへ WorldEdit で置き換える (`/mu worldtree` で手動切り替えも可能) |

ドキュメントサイトでは morinoparty の mpm リポジトリインデックスもホストしています。

📖 ドキュメント: https://utils.plugin.morino.party/

## 公開されるメトリクス (observability)

プロジェクト名の変更後も、メトリクス名は変更していません。

主要なメトリクスは以下のとおりです。完全な一覧は [Paper Metrics](https://utils.plugin.morino.party/docs/observability/paper/metrics) / [Velocity Metrics](https://utils.plugin.morino.party/docs/observability/velocity/metrics) を参照してください。

| メトリクス | 型 | 対象 | 内容 |
|-----------|-----|------|------|
| `minecraft_players_online` | gauge | Paper | オンラインプレイヤー数 |
| `minecraft_tps{period}` | gauge | Paper | TPS (直近 1 分 / 5 分 / 15 分) |
| `minecraft_tick_duration_seconds` | histogram | Paper | ティック時間 (MSPT) の分布 |
| `minecraft_world_entities{world}` など | gauge | Paper | ワールドごとのエンティティ数・チャンク数など |
| `minecraft_player_joins_total` / `minecraft_player_quits_total{reason}` | counter | Paper | プレイヤーの参加・退出回数 |
| `velocity_players_online` | gauge | Velocity | プロキシ全体のオンラインプレイヤー数 |
| `velocity_backend_players{server}` | gauge | Velocity | バックエンドサーバーごとの接続人数 |
| `velocity_logins_total` / `velocity_disconnects_total{status}` | counter | Velocity | ログイン・切断回数 |
| `jvm_*` / `process_*` | – | Paper / Velocity | JVM メモリ使用量、GC、スレッド数などのランタイム情報 |

メトリクスは `http://<host>:9225/metrics` (Paper) / `http://<host>:9226/metrics` (Velocity) で公開されます。
導入手順や設定については[ドキュメント](https://utils.plugin.morino.party/docs/observability/getting-started)を参照してください。

## モジュール構成

| モジュール | 説明 |
|-----------|------|
| `common` | Paper/Velocity 共通コード |
| `paper` | Paper (Bukkit) プラグイン |
| `velocity` | Velocity プロキシプラグイン |
| `api` | 外部プラグイン向け公開 API |

## 技術スタック

- **Kotlin** - 言語
- **Paper API** 26.2 - Minecraft サーバー API
- **Velocity API** 4.2 - Minecraft プロキシ API
- **Cloud** - コマンドフレームワーク (Incendo)
- **Koin** - 依存性注入
- **MCCoroutine** - Kotlin Coroutines の Minecraft 統合
- **ShadowJar** - Fat JAR 生成
- **Fumadocs** - ドキュメントサイト (Next.js)

## 必要環境

- **Java** 25 (Temurin 推奨)
- **Gradle** 9.x (Wrapper 同梱)
- **Node.js** 26+ / **pnpm** 10+ (ドキュメントビルド用)
- **[Task](https://taskfile.dev/)** (タスクランナー、任意)

## 開発

### AI エージェント向け指示ファイルの生成

`CLAUDE.md` / `AGENTS.md` は `.agent/rules/` から生成されるファイルで、git には含めていません (`.gitignore` 済み)。
clone 直後や `.agent/rules/` を編集したときに生成してください。

```bash
bash .agent/build.sh

# または Task を使用
task agent
```

### ビルド

```bash
# Gradle ビルド
./gradlew build -x test

# または Task を使用
task build
```

### 開発サーバー起動

```bash
# Paper テストサーバー
./gradlew :paper:runServer

# または Task を使用
task run
```

### ドキュメント開発

```bash
cd docs
pnpm install
pnpm dev

# または Task を使用
task docs
```

## Task コマンド一覧

| コマンド | 説明 |
|---------|------|
| `task build` | 全モジュールをビルド |
| `task run` | Paper 開発サーバーを起動 |
| `task docs` | ドキュメント開発サーバーを起動 |
| `task check` | フォーマット + ビルド |
| `task clear` | session.lock ファイルを削除 |
| `task license` | ライセンスヘッダーを付与・更新 (`spotlessApply`) |
| `task license:check` | ライセンスヘッダーを検証 (`spotlessCheck`) |
| `task agent` | `.agent/rules/` から `CLAUDE.md` / `AGENTS.md` を生成 |

## ビルド時定数 (BuildConstants)

`common/build.gradle.kts` の `generateBuildConstants` タスクが、ビルドのたびに `BuildConstants.kt` を生成します。

| 定数 | 値の由来 | 主な用途 |
|------|---------|---------|
| `BuildConstants.VERSION` | `gradle.properties` の `version` | Velocity の `@Plugin(version = ...)` |
| `BuildConstants.KOTLIN_VERSION` | `gradle/libs.versions.toml` の `kotlin` | Paper の `PluginLoader` が解決する `kotlin-stdlib` のバージョン |

Velocity モジュールでは kapt で `@Plugin` を処理して `velocity-plugin.json` を生成しています。

## GitHub Actions

| ワークフロー | トリガー | 説明 |
|-------------|---------|------|
| `check_pull_request.yml` | Pull Request | ビルドチェック |
| `preview.yml` | Pull Request | プレビュービルド・S3 アップロード・PR コメント |
| `upload.yml` | Release published | GitHub Release にJAR をアップロード |
| `release.yml` | Push to main | Release Drafter でドラフトリリース作成 |
| `deploy_docs.yml` | Push to main (docs/) | GitHub Pages (`utils.plugin.morino.party`) にドキュメントデプロイ |
| `dependabot_auto_merge.yml` | Dependabot PR | 自動マージ |
| `sync-label.yml` | labels.json 変更 | GitHub ラベル同期 |

## ライセンス

CC0-1.0

### ライセンスヘッダー

Kotlin ソースと `*.gradle.kts` には [Spotless](https://github.com/diffplug/spotless) でライセンスヘッダーを付与します。
ヘッダー本文は `config/spotless/license-header.kt` に一元管理されており、`$YEAR` が年に置き換わります (既存ファイルは `開始年-現在年` に更新されます)。

```bash
# ヘッダーを付与・更新
task license        # = ./gradlew spotlessApply

# ヘッダーの検証のみ
task license:check  # = ./gradlew spotlessCheck
```

`build` / `check` は失敗しない設定 (`isEnforceCheck = false`) なので、任意のタイミングで実行してください。
