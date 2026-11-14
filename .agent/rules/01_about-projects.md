## このアプリケーションの概要
「MoripaUtils」という、morinoparty 向けのユーティリティ Minecraft プラグインです。
リポジトリ名は `moripa-utils` です。

## 目的
morinoparty のサーバー運営に必要な機能を、機能 (feature) 単位で 1 つのプラグインにまとめて提供します。
現在、以下の機能があります。

- **observability** - Minecraft サーバー (Paper) およびプロキシ (Velocity) の稼働状況を Prometheus 形式で公開し、Grafana で可視化するための exporter
- **ticket** - `/ticket` コマンドからプレイヤーが運営への問い合わせ (チケット) を送信できる UI (MineAuth 導入時は HTTP API から自分のチケットを参照可能)
- **worldTree** - ロビーの世界樹の葉を、サーバーの起動時に月に応じた季節のブロックへ WorldEdit で置き換える (必ず中間ブロックを経由し、ブロック数の安全確認を行う。`/mu worldtree` で手動切り替えも可能)

また、docs サイトでは morinoparty の mpm リポジトリインデックスもホストしています。

### observability
主に以下のようなメトリクスを公開します。

- オンラインプレイヤー数 (Paper / Velocity)
- サーバーの TPS / MSPT (Paper)
- JVM のメモリ使用量などのランタイム情報

メトリクスは Prometheus からスクレイプされることを前提に設計してください。
メトリクス名は Prometheus の命名規約 (snake_case、単位を末尾に付ける、例: `minecraft_players_online`, `minecraft_tps`) に従ってください。
プロジェクト名を変更しても、既存のメトリクス名 (`minecraft_*` など) は変更しないでください。

## 主な技術スタック
- Kotlin (言語)
- Koin (依存性注入)
- PaperMC (MinecraftプラグインAPI)
- Velocity (MinecraftプロキシAPI)
- Cloud (コマンドフレームワーク)
- MCCoroutine (非同期処理)
- Prometheus (observability のメトリクス公開先。Grafana で可視化する)

## モジュール構成
- `common` - Paper/Velocity共通コード
- `paper` - Paper (Bukkit) プラグイン
- `velocity` - Velocity プロキシプラグイン
- `api` - 外部プラグイン向け公開API
