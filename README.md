# lucene-gosen-wikiparse

Wikipediaのデータをパースして、新旧の形態素解析ライブラリ（`lucene-gosen`）による結果を比較するためのツールです。
ソースコードを修正した際に、形態素解析の結果（コスト、単語、品詞）に意図しない変更がないかを確認することを目的としています。

## 必要条件

- Java 21 以上
- Gradle 8.5 以上（同梱の `gradlew` を使用可能）

## 事前準備

1. **Wikipediaデータの配置**
   以下のいずれかの方法でデータを用意してください：
   - Wikipedia XMLダンプファイルを配置（例：`./data/jawiki-latest-pages-articles.xml`）
   - Wiki-40B日本語データセット（Parquet形式）をダウンロード

2. **比較対象のJARファイルの用意**
   比較したい2つの `lucene-gosen` 関連の JAR ファイルを用意してください。

## 実行方法

### Wikipediaデータのダウンロード

#### 方法1: Wikipedia XMLダンプファイル

Wikipediaのダンプファイルをダウンロードするためのタスクを用意しています。

```powershell
.\gradlew runDownload
```

デフォルトでは日本語Wikipediaの最新の `pages-articles.xml.bz2` をダウンロードし、`./data/` ディレクトリに保存します。
任意のURLや保存先を指定することも可能です。

```powershell
.\gradlew runDownload --args="https://dumps.wikimedia.org/path/to/dump.xml.bz2 ./data/my-dump.xml.bz2"
```

#### 方法2: Wiki-40B日本語データセット（推奨）

Hugging Faceで公開されているWiki-40B日本語データセット（Parquet形式）をダウンロードできます。
このデータセットはファイルサイズが小さく（約1.3GB）、前処理済みで扱いやすいのが特徴です。

**全データセット（train, validation, test）をダウンロード：**
```powershell
.\gradlew runWiki40bDownload
```

**特定の分割のみダウンロード：**
```powershell
# testデータのみ（66.2MB）
.\gradlew runWiki40bDownload --args="./data/wiki40b-ja TEST"

# trainデータのみ（1.2GB）
.\gradlew runWiki40bDownload --args="./data/wiki40b-ja TRAIN"

# validationデータのみ（66.1MB）
.\gradlew runWiki40bDownload --args="./data/wiki40b-ja VALIDATION"
```

デフォルトの保存先は `./data/wiki40b-ja/` です。

### lucene-gosenのJARファイルのダウンロード

Maven Centralから`lucene-gosen`とその依存jar（`lucene-core`）を自動ダウンロードできます。

```powershell
.\gradlew runDownloadJars --args="6.2.1"
```

これにより、`./lib/6.2.1/` ディレクトリに以下のファイルがダウンロードされます：
- `lucene-gosen-6.2.1-ipadic.jar` (デフォルトでipadicを使用)
- `lucene-core-6.2.1.jar`

別のバージョンも同様にダウンロード可能です：

```powershell
.\gradlew runDownloadJars --args="6.0.1"
```

**注意**: Maven Centralで公開されているバージョンは限られています（6.0.1, 6.2.1など）。
利用可能なバージョンは[Maven Central](https://central.sonatype.com/artifact/com.github.lucene-gosen/lucene-gosen)で確認できます。

保存先ディレクトリを指定することも可能です：

```powershell
.\gradlew runDownloadJars --args="6.2.1 ./lib/custom-dir"
```

classifierを指定することも可能です（デフォルト: ipadic）：

```powershell
# naistdicを使用
.\gradlew runDownloadJars --args="6.2.1 ./lib/6.2.1 naistdic"

# classifierなしでダウンロード
.\gradlew runDownloadJars --args="6.2.1 ./lib/6.2.1 \"\""
```

### 形態素解析結果の比較

#### 方法1: Wikipedia XMLダンプファイルを使用

**JARファイルを直接指定：**
```powershell
.\gradlew runParser --args="path/to/old-gosen.jar path/to/new-gosen.jar"
```

**ディレクトリを指定（推奨）：**
ダウンロードしたjarファイルを使う場合、ディレクトリを指定すると、そのディレクトリ内の全てのjarファイルが自動的に読み込まれます：

```powershell
.\gradlew runParser --args="lib/6.0.1 lib/6.2.1"
```

これにより、各ディレクトリ内の`lucene-gosen`と`lucene-core`の両方が自動的に読み込まれ、正しく動作します。

**Wikipedia XMLファイルのパスを指定：**
```powershell
.\gradlew runParser --args="lib/6.0.1 lib/6.2.1 data/jawiki-latest-pages-articles.xml"
```

**実行引数：**
- `arg[0]`: 旧バージョンの JAR ファイルパスまたはディレクトリパス
- `arg[1]`: 新バージョンの JAR ファイルパスまたはディレクトリパス
- `arg[2]` (オプション): Wikipedia XMLファイルのパス（デフォルト: `./data/jawiki-latest-pages-articles.xml`）
- `arg[3]` (オプション): 処理する最大件数（デフォルト: 全件処理）
- `arg[4]` (オプション): レポート形式 (`text`|`html`|`both`、デフォルト: `both`)

**処理件数を制限した軽量テスト：**
```powershell
# 100件だけ処理（HTMLとテキストの両方を生成）
.\gradlew runParser --args="lib/6.0.1 lib/6.2.1 data/jawiki-latest-pages-articles.xml 100"

# HTMLレポートのみ生成
.\gradlew runParser --args="lib/6.0.1 lib/6.2.1 data/jawiki-latest-pages-articles.xml 100 html"

# テキストレポートのみ生成
.\gradlew runParser --args="lib/6.0.1 lib/6.2.1 data/jawiki-latest-pages-articles.xml 100 text"
```

#### 方法2: Wiki-40B Parquetファイルを使用

Wiki-40Bデータセットを使用する場合は、専用のパーサーを使用します：

```powershell
.\gradlew runWiki40bParser --args="lib/6.0.1 lib/6.2.1"
```

**特定のParquetファイルを指定：**
```powershell
# testデータを使用（最小サイズで動作確認に最適）
.\gradlew runWiki40bParser --args="lib/6.0.1 lib/6.2.1 ./data/wiki40b-ja/test.parquet"

# trainデータを使用（大規模なテスト）
.\gradlew runWiki40bParser --args="lib/6.0.1 lib/6.2.1 ./data/wiki40b-ja/train.parquet"
```

**実行引数：**
- `arg[0]`: 旧バージョンの JAR ファイルパスまたはディレクトリパス
- `arg[1]`: 新バージョンの JAR ファイルパスまたはディレクトリパス
- `arg[2]` (オプション): Parquetファイルのパス（デフォルト: `./data/wiki40b-ja/train.parquet`）
- `arg[3]` (オプション): 処理する最大件数（デフォルト: 全件処理）

**処理件数を制限した軽量テスト：**
```powershell
# 100件だけ処理
.\gradlew runWiki40bParser --args="lib/6.0.1 lib/6.2.1 ./data/wiki40b-ja/test.parquet 100"
```

## 出力結果

実行が完了すると、以下のレポートファイルが生成されます：

### Wikipedia XMLパーサー
- **HTMLレポート**: `diff_result.html` - 視覚的で詳細なレポート（推奨）
- **テキストレポート**: `diff_result.txt` - 従来のテキスト形式

### Wiki-40B Parquetパーサー
- `diff_result_wiki40b.txt`

### HTMLレポートの内容

**実行情報セクション**
- 実行開始/終了時刻、実行時間
- Old/New JARパスとファイル一覧
- Wikipedia XMLファイルパス
- 最大レコード数設定

**サマリーセクション**
- 総処理数、差分あり件数、一致件数、スキップ件数
- 差分率と一致率（パーセンテージ表示）
- カラフルなカード形式で視覚的に表示

**差分詳細セクション**
- 差分がある記事のテーブル表示
- 差分タイプ（cost/term/pos）のバッジ表示
- クリックで詳細を表示/非表示
- Old/Newの横並び比較表示
- 元のWikipediaテキストも表示可能

### テキストレポートの内容
- 実行情報ヘッダー
- `analyze result[cost] is different!!`: コストの合計値が異なる場合
- `analyze result[termList] is different!!`: 抽出された単語リストが異なる場合
- `analyze result[posList] is different!!`: 品詞リストが異なる場合
- 処理結果サマリー

## プロジェクト構造

- `src/main/java`: ソースコード
- `compilelib`: ビルド時に必要なライブラリ（`lucene-core-3.5.0.jar` など）
- `PROJECT_IMPROVEMENTS.md`: 今後の改善計画

## ライセンス

[Apache License, Version 2.0](LICENSE.txt)
