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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        int workerCount = config.getWorkerCount();
        int queueSize = config.getQueueSize();

        // データソース固有の初期化処理
        Object dataSource = initializeDataSource(config);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CompletionService<ProcessedRecord> completionService =
                new java.util.concurrent.ExecutorCompletionService<>(executor);
        Map<Long, ProcessedRecord> pendingResults = new HashMap<>();
        boolean inputExhausted = false;
        long submittedSeq = 0;
        long nextSeqToEmit = 0;
        int inFlight = 0;
        int scheduledCount = 0;
        int maxRecordCount = config.getMaxRecordCount();

        System.out.println("workers: " + workerCount + ", queueSize: " + queueSize);

        try {
            while (!inputExhausted || inFlight > 0) {
                while (!inputExhausted
                        && inFlight < queueSize
                        && (maxRecordCount <= 0 || scheduledCount < maxRecordCount)) {
                    WikipediaModel model = readNextModel(dataSource);
                    if (model == null) {
                        inputExhausted = true;
                        break;
                    }
                    if (!shouldProcessModel(model)) {
                        continue;
                    }

                    long sequence = submittedSeq++;
                    completionService.submit(() -> analyzeModel(sequence, model, analyzerManager));
                    inFlight++;
                    scheduledCount++;
                }
                if (!inputExhausted && maxRecordCount > 0 && scheduledCount >= maxRecordCount) {
                    inputExhausted = true;
                    System.out.println("Reached max record count: " + maxRecordCount);
                }

                if (inFlight == 0) {
                    continue;
                }

                ProcessedRecord doneRecord = waitForCompletedRecord(completionService);
                inFlight--;
                pendingResults.put(doneRecord.sequence(), doneRecord);

                while (pendingResults.containsKey(nextSeqToEmit)) {
                    ProcessedRecord record = pendingResults.remove(nextSeqToEmit);
                    nextSeqToEmit++;

                    if (record.error() != null) {
                        System.err.println("Error processing record #" + (counter + 1) +
                                (record.model().getTitle() != null ? " (Title: " + record.model().getTitle() + ")" : "") +
                                ": " + record.error().getMessage());
                        continue;
                    }

                    System.arraycopy(record.oldResult(), 0, oldResult, 0, RESULT_SIZE);
                    System.arraycopy(record.newResult(), 0, newResult, 0, RESULT_SIZE);
                    skippedCounter += record.skipped();
                    if (record.hasDifference()) {
                        falseCounter++;
                    }

                    for (ReportGenerator generator : reportGenerators) {
                        generator.addDiffResult(record.model(), oldResult, newResult, record.hasDifference(), printToConsole);
                    }

                    if (printToConsole) {
                        ParserUtils.printResults(counter, record.model(), oldResult, newResult);
                    }

                    if (counter % 1000 == 0) {
                        System.out.println("success count:" + counter);
                        for (ReportGenerator generator : reportGenerators) {
                            generator.flush();
                        }
                    }
                    counter++;
                }
            }
        } finally {
            executor.shutdownNow();
            try {
                closeDataSource(dataSource);
            } catch (Exception e) {
                System.err.println("Warning: failed to close data source: " + e.getMessage());
            }
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

    private ProcessedRecord waitForCompletedRecord(CompletionService<ProcessedRecord> completionService)
            throws InterruptedException, ExecutionException {
        Future<ProcessedRecord> future = completionService.take();
        return future.get();
    }

    private ProcessedRecord analyzeModel(long sequence, WikipediaModel model, AnalyzerContainerManager analyzerManager) {
        AnalyzeResult[] oldResult = ParserUtils.createAnalyzeResultArray(RESULT_SIZE);
        AnalyzeResult[] newResult = ParserUtils.createAnalyzeResultArray(RESULT_SIZE);
        try {
            int skipped = analyzerManager.getOldModelAnalyzer().analyze(
                    model, analyzerManager.getOldJarContainer(), oldResult);
            analyzerManager.getNewModelAnalyzer().analyze(
                    model, analyzerManager.getNewJarContainer(), newResult);
            boolean hasDifference = ParserUtils.compareResult(oldResult, newResult, RESULT_SIZE);
            return new ProcessedRecord(sequence, model, oldResult, newResult, skipped, hasDifference, null);
        } catch (Exception e) {
            return new ProcessedRecord(sequence, model, oldResult, newResult, 0, false, e);
        }
    }

    private record ProcessedRecord(long sequence, WikipediaModel model, AnalyzeResult[] oldResult,
                                   AnalyzeResult[] newResult, int skipped, boolean hasDifference, Exception error) {
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
