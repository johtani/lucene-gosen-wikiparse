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

> **Note**: 全てのコマンドは名前付きオプション形式に対応しています。`--help`オプションで使用可能なオプションを確認できます。

### Wikipediaデータのダウンロード

#### 方法1: Wikipedia XMLダンプファイル

Wikipediaのダンプファイルをダウンロードするためのタスクを用意しています。

```powershell
# デフォルトのURLと保存先を使用
.\gradlew runDownload

# URLと保存先を指定
.\gradlew runDownload --args="--url https://dumps.wikimedia.org/path/to/dump.xml.bz2 --destination ./data/my-dump.xml.bz2"

# ヘルプを表示
.\gradlew runDownload --args="--help"
```

**利用可能なオプション：**
- `--url`, `-u`: ダウンロード元URL（デフォルト: 日本語Wikipedia最新ダンプ）
- `--destination`, `-d`: 保存先ファイルパス（デフォルト: `./data/jawiki-latest-pages-articles.xml.bz2`）
- `--help`, `-h`: ヘルプメッセージを表示
- `--version`, `-V`: バージョン情報を表示

#### 方法2: Wiki-40B日本語データセット（推奨）

Hugging Faceで公開されているWiki-40B日本語データセット（Parquet形式）をダウンロードできます。
このデータセットはファイルサイズが小さく（約1.3GB）、前処理済みで扱いやすいのが特徴です。

```powershell
# 全データセット（train, validation, test）をダウンロード
.\gradlew runWiki40bDownload

# testデータのみ（66.2MB）
.\gradlew runWiki40bDownload --args="--split TEST"

# trainデータのみ（1.2GB）
.\gradlew runWiki40bDownload --args="--destination ./data/wiki40b-ja --split TRAIN"

# validationデータのみ（66.1MB）
.\gradlew runWiki40bDownload --args="--split VALIDATION"

# ヘルプを表示
.\gradlew runWiki40bDownload --args="--help"
```

**利用可能なオプション：**
- `--destination`, `-d`: 保存先ディレクトリ（デフォルト: `./data/wiki40b-ja`）
- `--split`, `-s`: ダウンロードするデータセット分割（`TRAIN`|`VALIDATION`|`TEST`|`ALL`、デフォルト: `ALL`）
- `--help`, `-h`: ヘルプメッセージを表示
- `--version`, `-V`: バージョン情報を表示

### lucene-gosenのJARファイルのダウンロード

Maven Centralから`lucene-gosen`とその依存jar（`lucene-core`）を自動ダウンロードできます。

```powershell
# バージョン6.2.1をダウンロード（デフォルト: ./lib/6.2.1/）
.\gradlew runDownloadJars --args="--version 6.2.1"

# バージョン6.0.1をダウンロード
.\gradlew runDownloadJars --args="--version 6.0.1"

# 保存先を指定
.\gradlew runDownloadJars --args="--version 6.2.1 --destination ./lib/custom-dir"

# classifierを指定（デフォルト: ipadic）
.\gradlew runDownloadJars --args="--version 6.2.1 --classifier naistdic"

# ヘルプを表示
.\gradlew runDownloadJars --args="--help"
```

これにより、指定したディレクトリに以下のファイルがダウンロードされます：
- `lucene-gosen-{version}-{classifier}.jar`
- `lucene-core-{version}.jar`

**利用可能なオプション：**
- `--version`, `-v`: ダウンロードするlucene-gosenのバージョン（必須）
- `--destination`, `-d`: 保存先ディレクトリ（デフォルト: `./lib/{version}`）
- `--classifier`, `-c`: classifier（デフォルト: `ipadic`）
- `--help`, `-h`: ヘルプメッセージを表示
- `--version`, `-V`: バージョン情報を表示

**注意**: Maven Centralで公開されているバージョンは限られています（6.0.1, 6.2.1など）。
利用可能なバージョンは[Maven Central](https://central.sonatype.com/artifact/com.github.lucene-gosen/lucene-gosen)で確認できます。

### 形態素解析結果の比較

#### 方法1: Wikipedia XMLダンプファイルを使用

```powershell
# 基本的な使い方（JARファイルまたはディレクトリを指定）
.\gradlew runParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1"

# Wikipedia XMLファイルのパスを指定
.\gradlew runParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --input data/jawiki-latest-pages-articles.xml"

# 処理件数を制限（100件だけ処理）
.\gradlew runParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --max-records 100"

# レポート形式を指定（HTMLのみ生成）
.\gradlew runParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --max-records 100 --format html"

# テキストレポートのみ生成
.\gradlew runParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --max-records 100 --format text"

# 全オプションを指定
.\gradlew runParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --input data/jawiki.xml --max-records 100 --format both"

# ヘルプを表示
.\gradlew runParser --args="--help"
```

**利用可能なオプション：**
- `--old-jar`, `-o`: 旧バージョンのJARファイルまたはディレクトリパス（必須）
- `--new-jar`, `-n`: 新バージョンのJARファイルまたはディレクトリパス（必須）
- `--input`, `-i`: Wikipedia XMLファイルのパス（デフォルト: `./data/jawiki-latest-pages-articles.xml`）
- `--max-records`, `-m`: 処理する最大件数（デフォルト: `-1`（全件処理））
- `--format`, `-f`: レポート形式（`text`|`html`|`both`、デフォルト: `both`）
- `--help`, `-h`: ヘルプメッセージを表示
- `--version`, `-V`: バージョン情報を表示

**Note**: ディレクトリを指定すると、そのディレクトリ内の全てのJARファイルが自動的に読み込まれます。

#### 方法2: Wiki-40B Parquetファイルを使用

Wiki-40Bデータセットを使用する場合は、専用のパーサーを使用します：

```powershell
# 基本的な使い方（デフォルト: train.parquet）
.\gradlew runWiki40bParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1"

# testデータを使用（最小サイズで動作確認に最適）
.\gradlew runWiki40bParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --input ./data/wiki40b-ja/test.parquet"

# trainデータを使用（大規模なテスト）
.\gradlew runWiki40bParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --input ./data/wiki40b-ja/train.parquet"

# 処理件数を制限（100件だけ処理）
.\gradlew runWiki40bParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --max-records 100"

# HTMLレポート生成
.\gradlew runWiki40bParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --input ./data/wiki40b-ja/test.parquet --max-records 100 --format html"

# 全オプションを指定
.\gradlew runWiki40bParser --args="--old-jar lib/6.0.1 --new-jar lib/6.2.1 --input ./data/wiki40b-ja/test.parquet --max-records 100 --format both"

# ヘルプを表示
.\gradlew runWiki40bParser --args="--help"
```

**利用可能なオプション：**
- `--old-jar`, `-o`: 旧バージョンのJARファイルまたはディレクトリパス（必須）
- `--new-jar`, `-n`: 新バージョンのJARファイルまたはディレクトリパス（必須）
- `--input`, `-i`: Parquetファイルのパス（デフォルト: `./data/wiki40b-ja/train.parquet`）
- `--max-records`, `-m`: 処理する最大件数（デフォルト: `-1`（全件処理））
- `--format`, `-f`: レポート形式（`text`|`html`|`both`、デフォルト: `text`）
- `--help`, `-h`: ヘルプメッセージを表示
- `--version`, `-V`: バージョン情報を表示

## 出力結果

実行が完了すると、以下のレポートファイルが生成されます：

### Wikipedia XMLパーサー
- **HTMLレポート**: `diff_result.html` - 視覚的で詳細なレポート（推奨）
- **テキストレポート**: `diff_result.txt` - 従来のテキスト形式

### Wiki-40B Parquetパーサー
- **HTMLレポート**: `diff_result_wiki40b.html` - 視覚的で詳細なレポート（`html`形式指定時）
- **テキストレポート**: `diff_result_wiki40b.txt` - 従来のテキスト形式（デフォルト）

### HTMLレポートの内容

**実行情報セクション**
- 実行開始/終了時刻、実行時間
- Old/New JARパスとファイル一覧
- データソースパス（Wikipedia XMLまたはWiki40b Parquet）
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
