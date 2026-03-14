# lucene-gosen-wikiparse

Wikipediaのデータをパースして、新旧の形態素解析ライブラリ（`lucene-gosen`）による結果を比較するためのツールです。
ソースコードを修正した際に、形態素解析の結果（コスト、単語、品詞）に意図しない変更がないかを確認することを目的としています。

## 必要条件

- Java 21 以上
- Gradle 8.5 以上（同梱の `gradlew` を使用可能）

## 事前準備

1. **Wikipediaデータの配置**
   `data` ディレクトリを作成し、WikipediaのXMLデータ（例：`jawiki-latest-pages-articles.xml`）を配置してください。
   現在の設定では `./data/jawiki-latest-pages-articles.xml` が参照されます。

2. **比較対象のJARファイルの用意**
   比較したい2つの `lucene-gosen` 関連の JAR ファイルを用意してください。

## 実行方法

### Wikipediaデータのダウンロード

Wikipediaのダンプファイルをダウンロードするためのタスクを用意しています。

```powershell
.\gradlew runDownload
```

デフォルトでは日本語Wikipediaの最新の `pages-articles.xml.bz2` をダウンロードし、`./data/` ディレクトリに保存します。
任意のURLや保存先を指定することも可能です。

```powershell
.\gradlew runDownload --args="https://dumps.wikimedia.org/path/to/dump.xml.bz2 ./data/my-dump.xml.bz2"
```

### lucene-gosenのJARファイルのダウンロード

Maven Centralから`lucene-gosen`とその依存jar（`lucene-core`）を自動ダウンロードできます。

```powershell
.\gradlew runDownloadJars --args="6.2.1"
```

これにより、`./lib/6.2.1/` ディレクトリに以下のファイルがダウンロードされます：
- `lucene-gosen-6.2.1.jar`
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

### 形態素解析結果の比較

**方法1: JARファイルを直接指定**
```powershell
.\gradlew runParser --args="path/to/old-gosen.jar path/to/new-gosen.jar"
```

**方法2: ディレクトリを指定（推奨）**
ダウンロードしたjarファイルを使う場合、ディレクトリを指定すると、そのディレクトリ内の全てのjarファイルが自動的に読み込まれます：

```powershell
.\gradlew runParser --args="lib/6.0.1 lib/6.2.1"
```

これにより、各ディレクトリ内の`lucene-gosen`と`lucene-core`の両方が自動的に読み込まれ、正しく動作します。

Wikipedia XMLファイルのパスを指定することも可能です：

```powershell
.\gradlew runParser --args="lib/6.0.1 lib/6.2.1 data/jawiki-latest-pages-articles.xml"
```

### 実行引数
- `arg[0]`: 旧バージョンの JAR ファイルパスまたはディレクトリパス
- `arg[1]`: 新バージョンの JAR ファイルパスまたはディレクトリパス
- `arg[2]` (オプション): Wikipedia XMLファイルのパス（デフォルト: `./data/jawiki-latest-pages-articles.xml`）

## 出力結果

実行が完了すると、差分がある場合に `diff_result.txt` に詳細が出力されます。

- `analyze result[cost] is different!!`: コストの合計値が異なる場合
- `analyze result[termList] is different!!`: 抽出された単語リストが異なる場合
- `analyze result[posList] is different!!`: 品詞リストが異なる場合

## プロジェクト構造

- `src/main/java`: ソースコード
- `compilelib`: ビルド時に必要なライブラリ（`lucene-core-3.5.0.jar` など）
- `PROJECT_IMPROVEMENTS.md`: 今後の改善計画

## ライセンス

[Apache License, Version 2.0](LICENSE.txt)
