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

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.wikipedia.WikipediaModel;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * テキスト形式のレポート生成クラス
 */
public class TextReportGenerator implements ReportGenerator {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int RESULT_SIZE = 2;

    private final BufferedWriter writer;
    private ExecutionInfo execInfo;
    private boolean headerWritten = false;

    public TextReportGenerator(String outputPath) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(outputPath));
    }

    @Override
    public void setExecutionInfo(ExecutionInfo info) {
        this.execInfo = info;
    }

    @Override
    public void addDiffResult(WikipediaModel model, AnalyzeResult[] oldResult,
                              AnalyzeResult[] newResult, boolean hasDifference,
                              boolean printToConsole) throws IOException {
        // ヘッダーをまだ書いていない場合は書く
        if (!headerWritten && execInfo != null) {
            writeHeader();
            headerWritten = true;
        }

        if (!hasDifference) {
            return; // 差分がない場合は何も書かない
        }

        // 既存のcompareResultロジックを使用
        compareAndWriteResult(model, oldResult, newResult, printToConsole);
    }

    private void writeHeader() throws IOException {
        writer.append("========================================");
        writer.newLine();
        writer.append("実行情報");
        writer.newLine();
        writer.append("========================================");
        writer.newLine();

        if (execInfo.getStartTime() != null) {
            writer.append("実行開始時刻: ").append(DATE_FORMAT.format(execInfo.getStartTime()));
            writer.newLine();
        }

        writer.append("Old JAR/Dir: ").append(execInfo.getOldJarPath());
        writer.newLine();
        if (execInfo.getOldJarFiles() != null && !execInfo.getOldJarFiles().isEmpty()) {
            for (String jarFile : execInfo.getOldJarFiles()) {
                writer.append("  - ").append(jarFile);
                writer.newLine();
            }
        }

        writer.append("New JAR/Dir: ").append(execInfo.getNewJarPath());
        writer.newLine();
        if (execInfo.getNewJarFiles() != null && !execInfo.getNewJarFiles().isEmpty()) {
            for (String jarFile : execInfo.getNewJarFiles()) {
                writer.append("  - ").append(jarFile);
                writer.newLine();
            }
        }

        writer.append("Wikipedia XML: ").append(execInfo.getXmlPath());
        writer.newLine();

        if (execInfo.getMaxRecordCount() > 0) {
            writer.append("最大レコード数: ").append(String.valueOf(execInfo.getMaxRecordCount())).append(" (制限あり)");
        } else {
            writer.append("最大レコード数: 全件処理");
        }
        writer.newLine();

        writer.newLine();
        writer.append("========================================");
        writer.newLine();
        writer.append("差分詳細");
        writer.newLine();
        writer.append("========================================");
        writer.newLine();
        writer.newLine();
    }

    private void compareAndWriteResult(WikipediaModel model, AnalyzeResult[] oldResult,
                                       AnalyzeResult[] newResult, boolean printToConsole) throws IOException {
        boolean different = false;

        // size check
        for (int i = 0; i < RESULT_SIZE; i++) {
            if (oldResult[i].getTotalCost() != newResult[i].getTotalCost()) {
                String msg = "analyze result[cost] is different!!";
                writer.append(msg);
                writer.newLine();
                if (printToConsole) System.out.println(msg);

                String oldMsg = "  old[" + oldResult[i].getTotalCost() + "]";
                writer.append(oldMsg);
                writer.newLine();
                if (printToConsole) System.out.println(oldMsg);

                String newMsg = "  new[" + newResult[i].getTotalCost() + "]";
                writer.append(newMsg);
                writer.newLine();
                if (printToConsole) System.out.println(newMsg);

                different = true;
            }
            if (different) {
                if (i == 0) {
                    writer.append(model.getTitle());
                    if (printToConsole) System.out.println("Title: " + model.getTitle());
                } else {
                    System.out.println(model.getText());
                }
                //System.exit(-1);
            }
            if (!oldResult[i].getTermList().equals(newResult[i].getTermList())) {
                String msg = "analyze result[termList] is different!!";
                writer.append(msg);
                writer.newLine();
                if (printToConsole) System.out.println(msg);

                String oldMsg = "  old[" + oldResult[i].getTermList().toString() + "]";
                writer.append(oldMsg);
                writer.newLine();
                if (printToConsole) System.out.println(oldMsg);

                String newMsg = "  new[" + newResult[i].getTermList().toString() + "]";
                writer.append(newMsg);
                writer.newLine();
                if (printToConsole) System.out.println(newMsg);

            }
            if (!oldResult[i].getPosList().equals(newResult[i].getPosList())) {
                String msg = "analyze result[posList] is different!!";
                writer.append(msg);
                writer.newLine();
                if (printToConsole) System.out.println(msg);

                String oldMsg = "  old[" + oldResult[i].getPosList().toString() + "]";
                writer.append(oldMsg);
                writer.newLine();
                if (printToConsole) System.out.println(oldMsg);

                String newMsg = "  new[" + newResult[i].getPosList().toString() + "]";
                writer.append(newMsg);
                writer.newLine();
                if (printToConsole) System.out.println(newMsg);

            }
        }
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void generateReport(String outputPath) throws IOException {
        // 最後にサマリーを追記
        if (execInfo != null) {
            writer.newLine();
            writer.append("========================================");
            writer.newLine();
            writer.append("処理結果サマリー");
            writer.newLine();
            writer.append("========================================");
            writer.newLine();

            if (execInfo.getEndTime() != null) {
                writer.append("実行終了時刻: ").append(DATE_FORMAT.format(execInfo.getEndTime()));
                writer.newLine();
            }

            if (execInfo.getDurationMs() > 0) {
                writer.append("実行時間: ").append(String.valueOf(execInfo.getDurationMs())).append(" msec");
                writer.newLine();
            }

            writer.append("total processed: ").append(String.valueOf(execInfo.getTotalProcessed()));
            writer.newLine();
            writer.append("falseCounter: ").append(String.valueOf(execInfo.getDifferenceCount()));
            writer.newLine();
            writer.append("skippedCounter: ").append(String.valueOf(execInfo.getSkippedCount()));
            writer.newLine();
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
