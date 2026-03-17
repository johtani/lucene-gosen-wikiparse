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
package lucene.gosen.wikipedia;

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.wikipedia.report.ExecutionInfo;
import lucene.gosen.wikipedia.report.HtmlReportGenerator;
import lucene.gosen.wikipedia.report.ReportGenerator;
import lucene.gosen.wikipedia.report.TextReportGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Wikipediaパーサーの抽象基底クラス
 * Template Methodパターンを使用してメイン処理ループを共通化
 */
public abstract class AbstractWikipediaParser {

    public static final int RESULT_SIZE = 2;

    /**
     * パーサーのメイン処理を実行
     */
    public void execute(ParserConfig config) throws Exception {
        long start = System.currentTimeMillis();

        // JARファイルを取得
        File[] oldJarFiles = ParserUtils.getJarFiles(config.getOldJarPath());
        File[] newJarFiles = ParserUtils.getJarFiles(config.getNewJarPath());

        // AnalyzerContainerManagerを作成
        AnalyzerContainerManager analyzerManager = new AnalyzerContainerManager(
                oldJarFiles, newJarFiles);

        // 実行情報を構築
        ExecutionInfo execInfo = ParserUtils.buildExecutionInfo(
                config,
                oldJarFiles,
                newJarFiles,
                getDataSourceType(),
                start);

        // レポートジェネレーターを初期化
        List<ReportGenerator> reportGenerators = createReportGenerators(config, execInfo);

        System.out.println("start :: " + new Date(start));

        // AnalyzeResult配列を初期化
        AnalyzeResult[] oldResult = ParserUtils.createAnalyzeResultArray(RESULT_SIZE);
        AnalyzeResult[] newResult = ParserUtils.createAnalyzeResultArray(RESULT_SIZE);

        // カウンター初期化
        int counter = 0;
        int falseCounter = 0;
        int skippedCounter = 0;
        boolean printToConsole = config.shouldPrintToConsole();

        // データソース固有の初期化処理
        Object dataSource = initializeDataSource(config);

        try {
            // メイン処理ループ
            WikipediaModel model;
            while ((model = readNextModel(dataSource)) != null) {
                if (shouldProcessModel(model)) {
                    try {
                        // 解析実行
                        int skipped = analyzerManager.getOldModelAnalyzer().analyze(
                                model, analyzerManager.getOldJarContainer(), oldResult);
                        analyzerManager.getNewModelAnalyzer().analyze(
                                model, analyzerManager.getNewJarContainer(), newResult);
                        skippedCounter += skipped;

                        // 結果比較
                        boolean hasDifference = ParserUtils.compareResult(oldResult, newResult, RESULT_SIZE);
                        if (hasDifference) {
                            falseCounter++;
                        }

                        // レポートに結果を追加
                        for (ReportGenerator generator : reportGenerators) {
                            generator.addDiffResult(model, oldResult, newResult, hasDifference, printToConsole);
                        }

                        // コンソール出力
                        if (printToConsole) {
                            ParserUtils.printResults(counter, model, oldResult, newResult);
                        }

                        // 定期的なフラッシュ
                        if (counter % 1000 == 0) {
                            System.out.println("success count:" + counter);
                            for (ReportGenerator generator : reportGenerators) {
                                generator.flush();
                            }
                        }
                        counter++;

                        // 最大レコード数チェック
                        if (config.getMaxRecordCount() > 0 && counter >= config.getMaxRecordCount()) {
                            System.out.println("Reached max record count: " + config.getMaxRecordCount());
                            break;
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing record #" + (counter + 1) +
                                (model.getTitle() != null ? " (Title: " + model.getTitle() + ")" : "") +
                                ": " + e.getMessage());
                        // Continue processing next record
                    }
                }
            }
        } finally {
            // データソースのクリーンアップ
            closeDataSource(dataSource);
        }

        // 実行情報の最終更新
        ParserUtils.finalizeExecutionInfo(execInfo, counter, falseCounter, skippedCounter, start);

        // レポート生成
        generateReports(reportGenerators);

        // 統計情報の出力
        System.out.println("total processed: " + counter);
        System.out.println("falseCounter: " + falseCounter);
        System.out.println("skippedCounter: " + skippedCounter);
        System.out.println((System.currentTimeMillis() - start) + "msec");
    }

    /**
     * レポートジェネレーターを作成
     */
    protected List<ReportGenerator> createReportGenerators(ParserConfig config, ExecutionInfo execInfo)
            throws IOException {
        List<ReportGenerator> reportGenerators = new ArrayList<>();

        if (config.getReportFormat().equals("text") || config.getReportFormat().equals("both")) {
            TextReportGenerator textGen = new TextReportGenerator(getTextReportFileName());
            textGen.setExecutionInfo(execInfo);
            reportGenerators.add(textGen);
        }
        if (config.getReportFormat().equals("html") || config.getReportFormat().equals("both")) {
            HtmlReportGenerator htmlGen = new HtmlReportGenerator();
            htmlGen.setExecutionInfo(execInfo);
            reportGenerators.add(htmlGen);
        }

        return reportGenerators;
    }

    /**
     * レポートを生成
     */
    protected void generateReports(List<ReportGenerator> reportGenerators) throws IOException {
        for (ReportGenerator generator : reportGenerators) {
            if (generator instanceof TextReportGenerator) {
                generator.generateReport(getTextReportFileName());
                System.out.println("Text report generated: " + getTextReportFileName());
            } else if (generator instanceof HtmlReportGenerator) {
                generator.generateReport(getHtmlReportFileName());
                System.out.println("HTML report generated: " + getHtmlReportFileName());
            }
            generator.close();
        }
    }

    /**
     * データソースタイプを取得（"XML" または "Parquet"）
     */
    protected abstract String getDataSourceType();

    /**
     * データソースを初期化
     */
    protected abstract Object initializeDataSource(ParserConfig config) throws Exception;

    /**
     * 次のモデルを読み込む
     *
     * @return 次のモデル、データが無い場合はnull
     */
    protected abstract WikipediaModel readNextModel(Object dataSource) throws Exception;

    /**
     * モデルを処理すべきかどうかを判定
     */
    protected abstract boolean shouldProcessModel(WikipediaModel model);

    /**
     * データソースをクローズ
     */
    protected abstract void closeDataSource(Object dataSource) throws Exception;

    /**
     * テキストレポートのファイル名を取得
     */
    protected abstract String getTextReportFileName();

    /**
     * HTMLレポートのファイル名を取得
     */
    protected abstract String getHtmlReportFileName();
}
