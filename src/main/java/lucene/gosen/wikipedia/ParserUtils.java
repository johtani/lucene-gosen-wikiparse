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

import java.io.File;
import java.util.Arrays;
import java.util.Date;

/**
 * Wikipedia パーサーで使用する共通ユーティリティメソッド
 */
public class ParserUtils {

    /**
     * 指定されたパスからJARファイルの配列を取得する
     * ディレクトリが指定された場合は、そのディレクトリ内の全てのJARファイルを返す
     * ファイルが指定された場合は、そのファイルのみを含む配列を返す
     */
    public static File[] getJarFiles(String path) {
        File file = new File(path);

        if (file.isDirectory()) {
            // ディレクトリの場合、.jarファイルのみをフィルタリング
            File[] jarFiles = file.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (jarFiles == null || jarFiles.length == 0) {
                throw new RuntimeException("No JAR files found in directory: " + path);
            }
            System.out.println("Found " + jarFiles.length + " JAR file(s) in " + path);
            for (File jarFile : jarFiles) {
                System.out.println("  - " + jarFile.getName());
            }
            return jarFiles;
        } else if (file.isFile()) {
            // ファイルの場合、単一のファイルを含む配列を返す
            System.out.println("Using JAR file: " + file.getName());
            return new File[]{file};
        } else {
            throw new RuntimeException("Path is neither a file nor a directory: " + path);
        }
    }

    /**
     * 解析結果をコンソールに出力する
     */
    public static void printResults(int counter, WikipediaModel model,
                                    AnalyzeResult[] oldResult, AnalyzeResult[] newResult) {
        System.out.println("\n=== Record #" + (counter + 1) + " ===");
        System.out.println("Title: \"" + model.getTitle() + "\"");
        System.out.println("Text: \"" + model.getText() + "\"");
        System.out.println("\n[OLD Results]");
        System.out.println("  Terms: " + oldResult[0].getTermList());
        System.out.println("  POS: " + oldResult[0].getPosList());
        System.out.println("  Total Cost: " + oldResult[0].getTotalCost());
        System.out.println("\n[NEW Results]");
        System.out.println("  Terms: " + newResult[0].getTermList());
        System.out.println("  POS: " + newResult[0].getPosList());
        System.out.println("  Total Cost: " + newResult[0].getTotalCost());
    }

    /**
     * AnalyzeResult配列を初期化する
     */
    public static AnalyzeResult[] createAnalyzeResultArray(int size) {
        AnalyzeResult[] results = new AnalyzeResult[size];
        for (int i = 0; i < size; i++) {
            results[i] = new AnalyzeResult();
        }
        return results;
    }

    /**
     * 解析結果を比較する
     *
     * @param model      WikipediaModel
     * @param oldResult  旧バージョンの解析結果
     * @param newResult  新バージョンの解析結果
     * @param resultSize 比較する結果のサイズ
     * @return 差分がある場合はtrue
     */
    public static boolean compareResult(WikipediaModel model, AnalyzeResult[] oldResult,
                                        AnalyzeResult[] newResult, int resultSize) {
        boolean different = false;

        // size check
        for (int i = 0; i < resultSize; i++) {
            if (oldResult[i].getTotalCost() != newResult[i].getTotalCost()) {
                different = true;
            }
            if (!oldResult[i].getTermList().equals(newResult[i].getTermList())) {
                different = true;
            }
            if (!oldResult[i].getPosList().equals(newResult[i].getPosList())) {
                different = true;
            }
            break;
        }
        return different;
    }

    /**
     * ExecutionInfoオブジェクトを構築する
     *
     * @param config         パーサー設定
     * @param oldJarFiles    旧バージョンのJARファイル配列
     * @param newJarFiles    新バージョンのJARファイル配列
     * @param dataSourceType データソースタイプ ("XML" または "Parquet")
     * @param startTime      開始時刻
     * @return ExecutionInfo
     */
    public static ExecutionInfo buildExecutionInfo(ParserConfig config, File[] oldJarFiles,
                                                   File[] newJarFiles, String dataSourceType, long startTime) {
        ExecutionInfo execInfo = new ExecutionInfo();
        execInfo.setOldJarPath(config.getOldJarPath());
        execInfo.setNewJarPath(config.getNewJarPath());
        execInfo.setDataSourcePath(config.getInputPath());
        execInfo.setDataSourceType(dataSourceType);
        execInfo.setMaxRecordCount(config.getMaxRecordCount());
        execInfo.setReportFormat(config.getReportFormat());
        execInfo.setStartTime(new Date(startTime));
        execInfo.setOldJarFiles(Arrays.stream(oldJarFiles).map(File::getName).toList());
        execInfo.setNewJarFiles(Arrays.stream(newJarFiles).map(File::getName).toList());
        return execInfo;
    }

    /**
     * ExecutionInfoを最終更新する
     *
     * @param execInfo       ExecutionInfo
     * @param counter        処理済みカウント
     * @param falseCounter   差分カウント
     * @param skippedCounter スキップカウント
     * @param startTime      開始時刻
     */
    public static void finalizeExecutionInfo(ExecutionInfo execInfo, int counter, int falseCounter,
                                             int skippedCounter, long startTime) {
        execInfo.setEndTime(new Date());
        execInfo.setDurationMs(System.currentTimeMillis() - startTime);
        execInfo.setTotalProcessed(counter);
        execInfo.setDifferenceCount(falseCounter);
        execInfo.setSkippedCount(skippedCounter);
    }
}
