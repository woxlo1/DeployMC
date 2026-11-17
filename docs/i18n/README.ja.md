<!--
Translation status
- Source: README.md
- Source commit: 2983407
- Completeness: complete draft
- Review: AI-assisted draft, not reviewed by a native speaker
- Reviewer: none
- Last reviewed: not reviewed
-->

# DeployMC

[English](../../README.md) · 日本語

**Minecraft サーバー向けの、安全で協調的なプラグイン配備とファイル管理。一流の公開 API を備えています。**

> **状態: プレアルファ（0.1.0 基盤）。** DeployMC は開発を始めたばかりです。
> 現在のリポジトリには、プロジェクト文書、仕様、ビルドの骨組みが含まれています。
> 以下に挙げるユーザー向け機能のほとんどは**まだ実装されていません**。この README は
> 意図する製品を示し、現在実際に動作するものを明確に記載します。

> **セキュリティ:** JAR の配備はサーバー上でのコード実行です。DeployMC は運用上のリスクを
> 減らしますが、悪意あるアカウントによる JAR 配備を安全にはできません。配備権限を与える前に
> セキュリティモデルをお読みください。

## 現在動作するもの

| 領域 | 状態 |
|---|---|
| リポジトリ、ガバナンス、開発文書 | 進行中（フェーズ 0） |
| Java、Web、CLI、Python SDK のビルド骨組み | ビルド可能。製品機能はなし |
| アーキテクチャの基準文書と ADR | 起草済み（フェーズ 0） |
| CI ワークフローとブランチ保護 | `main` で稼働中 |
| 仕様（REST、WebSocket、設定） | 未着手（フェーズ 0） |
| プラグインランタイム、API、Web UI、CLI、SDK | 未着手（フェーズ 1 以降） |

常に最新の詳細な状態は
[`docs/development/implementation-status.md`](../development/implementation-status.md) で管理します。

## DeployMC を選ぶ理由

Minecraft サーバーを開発するチームは、通常、JAR ファイルと YAML の編集をホストを管理する
一人に任せます。その人がボトルネックになり、代わりに SSH や SFTP の資格情報を共有すると
マシン全体が露出します。

DeployMC は、プラグインディレクトリだけを対象にした、狭く、監査可能で、認証済みの層を
提供することを目指します。サンドボックス内でファイルを編集し、JAR をスナップショットと
ロールバックを伴うトランザクションとして配備し、コンソールを監視し、CI から配備を自動化
できます。その際、ホストの資格情報を渡す必要はありません。

秘密鍵、SSH アクセス、ホストのファイルシステムは明確に対象外です。

## 計画している機能

- プラグインディレクトリ向けのサンドボックス化されたファイルエクスプローラーとエディター
- 検証、制御された再起動、ヘルスチェック、ロールバックを伴うトランザクション形式の JAR 配備
- 差分表示とワンクリック復元を備えた自動スナップショット
- フィルタリングとポリシー制御されたコマンドを備えたリアルタイムコンソール配信
- アイデンティティ、RBAC、監査ログ。Cloudflare Access 連携とトークンモード
- バージョン管理された REST/WebSocket API、Java API、SDK、CLI、GitHub Action
- 実際の CI 互換性マトリクスで検証する、複数バージョン・複数プラットフォーム対応
- 英語と日本語を起点とした、文書とインターフェースの多言語化

## アーキテクチャ

計画中のアーキテクチャは [`docs/architecture/overview.md`](../architecture/overview.md) に
記載しています。あわせて
[プラットフォーム SPI](../architecture/platform-spi.md)、
[配備状態マシン](../architecture/deployment-state-machine.md)、
[スレッドモデル](../architecture/threading-model.md)、
[API の階層](../architecture/api-layering.md) の各文書があります。重要な決定は
[アーキテクチャ決定記録（ADR）](../adr/README.md) に残し、セキュリティ上の立場は
[脅威モデルの草案](../../specification/security/threat-model.md) に記載しています。

完全な開発仕様は
[`docs/development/DeployMC_Complete_Master_Development_Specification.md`](../development/DeployMC_Complete_Master_Development_Specification.md)
にあります。

## 対象外

- DeployMC は汎用のリモートシェル、SFTP サーバー、ホスティングパネルではありません。
- DeployMC は既定で、オペレーティングシステム、ワールド、サーバー本体のファイルを管理しません。
- DeployMC はワールドやプレイヤーデータのバックアップシステムを置き換えるものではありません。
- DeployMC は悪意あるチームメンバーを無害化するものではありません。JAR 配備はコード実行です。

## 互換性

Minecraft のバージョン、サーバープラットフォーム、Java ランタイムの組み合わせは、まだ何も検証
していません。DeployMC は、実際の互換性ハーネスが生成した証拠だけに基づいてサポートティアを
割り当てる方針です。

| ティア | 意味 | 現在の登録 |
|---|---|---|
| ティア 1 | すべてのプルリクエストで実サーバーを使って検証 | なし |
| ティア 2 | 定期的に検証 | なし |
| ティア 3 | ベストエフォート。動作する可能性はあるが未検証 | なし |

それまでは、このリポジトリのプラットフォームに関する記述は、サポート済みの構成ではなく設計目標
として扱ってください。ティアを獲得するための規則、各登録に必要な証拠、未解決の障壁は
[`docs/compatibility/support-matrix.md`](../compatibility/support-matrix.md) に定義しています。

## プロジェクトの状態と文書

ガバナンス、コントリビューション、運用の文書はリポジトリ直下にあります。開発仕様（
[DeployMC_Complete_Master_Development_Specification.md](../development/DeployMC_Complete_Master_Development_Specification.md)）が正式な情報源です。
開発の進捗文書は [`docs/development/`](../development/) にあります。

## コミュニティとコントリビューション

- [コントリビューションガイド](../../CONTRIBUTING.md) — セットアップ、ビルドコマンド、コミットとプルリクエストのルール
- [行動規範](../../CODE_OF_CONDUCT.md)
- [セキュリティポリシー](../../SECURITY.md) — **脆弱性は公開 Issue ではなく非公開で報告してください**
- [サポート](../../SUPPORT.md) — 質問先、記載すべき情報、対応目標
- [ロードマップ](../../ROADMAP.md) — フェーズの順序とマイルストーンのバージョン（日程ではありません）
- [ガバナンス](../../GOVERNANCE.md) — 現在の意思決定方法
- [作者](../../AUTHORS.md) と [変更履歴](../../CHANGELOG.md)

## ライセンス

DeployMC は [Apache License 2.0](../../LICENSE) の下で提供されます。

## 非提携

DeployMC は Mojang Studios、Microsoft、Cloudflare と提携していません。Minecraft は
Mojang Studios の商標です。Cloudflare および Cloudflare Access は Cloudflare, Inc. の商標です。
