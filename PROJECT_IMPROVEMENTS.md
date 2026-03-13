# プロジェクトの改善点と作業計画

現状の `lucene-gosen-wikiparse` プロジェクトを徹底的に確認した結果、以下の改善点と推奨作業が特定されました。

## 1. コードの品質とメンテナンス性 (高優先度)

*   **例外処理の近代化**:
    *   `PagesArticlesXmlParser.java` で `System.exit(-1)` や `printStackTrace()` が使用されています。これらを適切な例外スローや、後述するロギングライブラリへの移行に置き換える必要があります。
*   **リフレクションの最適化**:
    *   `WikipediaModelAnalyzer.java` の 51 行目にある `TODO getCost execute by reflaction` の通り、現在は `getMethod("getCost").invoke(...)` でコストを取得しています。大量のテキストを処理する場合、`MethodHandle` や、共通インターフェース（または型キャスト）の利用を検討すべきです。
*   **マジックナンバーの排除**:
    *   `PagesArticlesXmlParser.java` の `RESULT_SIZE = 2` (タイトルと本文) など、構成が固定されています。これを動的に変更可能にすると、比較対象を増やしたり柔軟性が向上します。
*   **不自然な例外キャッチの修正**:
    *   `ComponentContainer.java` で `MalformedURLException` を `RuntimeException` でラップして投げている箇所や、`WikipediaModelAnalyzer.java` での `ClassNotFoundException` の扱いなどを整理する必要があります。

## 2. 実行環境と機能拡張 (中優先度)

*   **ロギングの導入**:
    *   `System.out` / `System.err` ではなく、SLF4J + Logback/Reload4j 等のロギングフレームワークを導入し、解析の進捗やエラー詳細を適切に記録できるようにします。
*   **圧縮ファイルへの対応**:
    *   Wikipedia の XML ダンプは巨大なため、`.bz2` や `.gz` 形式のまま読み込めるように `Apache Commons Compress` 等を導入することを推奨します。
*   **詳細なレポート出力**:
    *   現在は `diff_result.txt` にテキストで差分を出力していますが、どの記事でどの程度の差分が出たのかを可視化する HTML レポートなどの出力機能があると便利です。
*   **Lucene/Gosen の最新版対応**:
    *   現在は Lucene 3.5.0 に依存していますが、Lucene 9.x/10.x 系への対応や、依存関係の Maven 中央リポジトリ経由での取得（`compilelib` からの脱却）が望まれます。

## 3. テストとドキュメント (中優先度)

*   **自動テストの拡充**:
    *   `RestrictedURLClassLoader` や `ComponentContainer` の動作を確認するための JUnit テストが不足しています。特にクラスロードの境界条件をテストする必要があります。
*   **~~README の充実~~**:
    *   ~~引数の詳細や、解析対象とする Lucene/Gosen のバージョンに関する制約事項などを明記する必要があります。~~

## 4. プロファイリングとパフォーマンス (低優先度)

*   **マルチスレッド化**:
    *   巨大な XML の解析を高速化するため、XML のパースと形態素解析を分離し、形態素解析部分を並列処理（Worker スレッド）にする構成が考えられます。

---
作成日: 2026-03-13
作成者: Junie
