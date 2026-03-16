/*
 * Copyright 2012 Jun Ohtani
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lucene.gosen.wikipedia.report;

import java.io.IOException;

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.wikipedia.WikipediaModel;

/**
 * レポート生成のインターフェース
 */
public interface ReportGenerator {

    /**
     * 実行情報を設定
     * @param info 実行情報
     */
    void setExecutionInfo(ExecutionInfo info);

    /**
     * 差分結果を追加
     * @param model Wikipediaモデル
     * @param oldResult 旧バージョンの解析結果
     * @param newResult 新バージョンの解析結果
     * @param hasDifference 差分があるかどうか
     * @param printToConsole コンソールに出力するかどうか
     * @throws IOException IO例外
     */
    void addDiffResult(WikipediaModel model, AnalyzeResult[] oldResult,
                      AnalyzeResult[] newResult, boolean hasDifference,
                      boolean printToConsole) throws IOException;

    /**
     * レポートを生成
     * @param outputPath 出力パス
     * @throws IOException IO例外
     */
    void generateReport(String outputPath) throws IOException;

    /**
     * 定期的なフラッシュ（大量データ処理時のメモリ対策）
     * @throws IOException IO例外
     */
    void flush() throws IOException;

    /**
     * リソースのクローズ
     * @throws IOException IO例外
     */
    void close() throws IOException;
}
