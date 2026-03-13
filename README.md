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

Gradle の `runParser` タスクを使用して実行します。引数として、比較する2つの JAR ファイルのパスを渡します。

```powershell
.\gradlew runParser --args="path/to/old-gosen.jar path/to/new-gosen.jar"
```

### 実行引数
- `arg[0]`: 旧バージョンの JAR ファイルパス
- `arg[1]`: 新バージョンの JAR ファイルパス

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
