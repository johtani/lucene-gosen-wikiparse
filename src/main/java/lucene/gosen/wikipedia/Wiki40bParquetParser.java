package lucene.gosen.wikipedia;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.Converter;
import org.apache.parquet.io.api.GroupConverter;
import org.apache.parquet.io.api.PrimitiveConverter;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.schema.MessageType;

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.test.util.ComponentContainer;
import lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer;
import lucene.gosen.wikipedia.report.ExecutionInfo;
import lucene.gosen.wikipedia.report.HtmlReportGenerator;
import lucene.gosen.wikipedia.report.ReportGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wiki-40B ParquetファイルをパースしてWikipediaModelに変換するクラス
 */
public class Wiki40bParquetParser {

    public static final int RESULT_SIZE = 2;

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        if (args.length < 2) {
            System.out.println("arg[0] is old jar or directory, arg[1] is new jar or directory, [arg[2] is parquet file (default: ./data/wiki40b-ja/train.parquet)], [arg[3] is max record count (optional, default: all records)], [arg[4] is report format (txt or html, default: txt)]");
            System.exit(-1);
        }

        String parquetPath = args.length >= 3 ? args[2] : "./data/wiki40b-ja/train.parquet";

        // Parse max record count with validation
        int maxRecordCount = -1; // -1 means no limit
        if (args.length >= 4) {
            try {
                maxRecordCount = Integer.parseInt(args[3]);
                if (maxRecordCount <= 0) {
                    System.err.println("Error: max record count must be a positive number");
                    System.exit(-1);
                }
            } catch (NumberFormatException e) {
                System.err.println("Error: arg[3] must be a valid number, got: " + args[3]);
                System.exit(-1);
            }
        }

        // Parse report format
        String reportFormat = args.length >= 5 ? args[4].toLowerCase() : "txt";
        if (!reportFormat.equals("txt") && !reportFormat.equals("html")) {
            System.err.println("Error: report format must be 'txt' or 'html', got: " + reportFormat);
            System.exit(-1);
        }

        // レポート生成の準備
        ExecutionInfo execInfo = new ExecutionInfo();
        execInfo.setOldJarPath(args[0]);
        execInfo.setNewJarPath(args[1]);
        execInfo.setDataSourcePath(parquetPath);
        execInfo.setDataSourceType("Parquet");
        execInfo.setMaxRecordCount(maxRecordCount);
        execInfo.setReportFormat(reportFormat);
        execInfo.setStartTime(new Date(start));

        ReportGenerator reportGenerator = null;
        BufferedWriter bw = null;

        if ("html".equals(reportFormat)) {
            reportGenerator = new HtmlReportGenerator();
            reportGenerator.setExecutionInfo(execInfo);
        } else {
            bw = new BufferedWriter(new FileWriter("diff_result_wiki40b.txt"));
        }

        System.out.println("start :: " + new Date(start));
        File[] oldJarFilesArray = getJarFiles(args[0]);
        File[] newJarFilesArray = getJarFiles(args[1]);

        // JAR情報をExecutionInfoに設定
        execInfo.setOldJarFiles(Arrays.stream(oldJarFilesArray).map(File::getName).collect(Collectors.toList()));
        execInfo.setNewJarFiles(Arrays.stream(newJarFilesArray).map(File::getName).collect(Collectors.toList()));

        ComponentContainer oldJarContainer = new ComponentContainer(oldJarFilesArray);
        ComponentContainer newJarContainer = new ComponentContainer(newJarFilesArray);

        WikipediaModelAnalyzer oldModelAnalyzer = (WikipediaModelAnalyzer) oldJarContainer.createComponent(
                "lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer", null, null);
        WikipediaModelAnalyzer newModelAnalyzer = (WikipediaModelAnalyzer) newJarContainer.createComponent(
                "lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer", null, null);

        AnalyzeResult[] oldResult = new AnalyzeResult[RESULT_SIZE];
        AnalyzeResult[] newResult = new AnalyzeResult[RESULT_SIZE];
        for (int i = 0; i < RESULT_SIZE; i++) {
            oldResult[i] = new AnalyzeResult();
            newResult[i] = new AnalyzeResult();
        }

        Configuration conf = new Configuration();
        Path path = new Path(parquetPath);

        try (ParquetReader<WikipediaModel> reader = ParquetReader.builder(new Wiki40bReadSupport(), path)
                .withConf(conf)
                .build()) {

            int counter = 0;
            int falseCounter = 0;
            int skippedCounter = 0;
            WikipediaModel model;
            boolean printToConsole = (maxRecordCount > 0 && maxRecordCount <= 10);

            while ((model = reader.read()) != null) {
                if (model.getText() != null && !model.getText().isEmpty()) {
                    int skipped = oldModelAnalyzer.analyze(model, oldJarContainer, oldResult);
                    newModelAnalyzer.analyze(model, newJarContainer, newResult);
                    skippedCounter += skipped;

                    boolean hasDifference = false;
                    if (reportGenerator != null) {
                        hasDifference = compareResult(null, model, oldResult, newResult, printToConsole);
                        reportGenerator.addDiffResult(model, oldResult, newResult, hasDifference, printToConsole);
                    } else {
                        hasDifference = compareResult(bw, model, oldResult, newResult, printToConsole);
                    }

                    if (hasDifference) {
                        falseCounter++;
                    }

                    if (printToConsole) {
                        printResults(counter, model, oldResult, newResult);
                    }

                    if (counter % 1000 == 0) {
                        System.out.println("success count:" + counter);
                        if (bw != null) {
                            bw.flush();
                        }
                        if (reportGenerator != null) {
                            reportGenerator.flush();
                        }
                    }
                    counter++;

                    // Check if max record count is reached
                    if (maxRecordCount > 0 && counter >= maxRecordCount) {
                        System.out.println("Reached max record count: " + maxRecordCount);
                        break;
                    }
                }
            }

            // 処理結果をExecutionInfoに設定
            execInfo.setTotalProcessed(counter);
            execInfo.setDifferenceCount(falseCounter);
            execInfo.setSkippedCount(skippedCounter);
            execInfo.setEndTime(new Date());
            execInfo.setDurationMs(System.currentTimeMillis() - start);

            // レポート生成
            if (bw != null) {
                bw.close();
            }

            if (reportGenerator != null) {
                String outputPath = "diff_result_wiki40b.html";
                reportGenerator.generateReport(outputPath);
                reportGenerator.close();
                System.out.println("HTML report generated: " + outputPath);
            }

            System.out.println("total processed: " + counter);
            System.out.println("falseCounter: " + falseCounter);
            System.out.println("skippedCounter: " + skippedCounter);
            System.out.println((System.currentTimeMillis() - start) + "msec");
        }
    }

    private static boolean compareResult(BufferedWriter bw, WikipediaModel model,
                                         AnalyzeResult[] oldResult, AnalyzeResult[] newResult, boolean printToConsole) throws IOException {
        boolean different = false;

        // size check
        for (int i = 0; i < RESULT_SIZE; i++) {
            if (oldResult[i].getTotalCost() != newResult[i].getTotalCost()) {
                String msg = "analyze result[cost] is different!!";
                if (bw != null) {
                    bw.append(msg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(msg);

                String oldMsg = "  old[" + oldResult[i].getTotalCost() + "]";
                if (bw != null) {
                    bw.append(oldMsg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(oldMsg);

                String newMsg = "  new[" + newResult[i].getTotalCost() + "]";
                if (bw != null) {
                    bw.append(newMsg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(newMsg);

                different = true;
            }
            if (different) {
                if (i == 0) {
                    if (bw != null) {
                        bw.append(model.title);
                    }
                    if (printToConsole) System.out.println("Title: " + model.title);
                }
            }
            if (!oldResult[i].getTermList().equals(newResult[i].getTermList())) {
                String msg = "analyze result[termList] is different!!";
                if (bw != null) {
                    bw.append(msg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(msg);

                String oldMsg = "  old[" + oldResult[i].getTermList().toString() + "]";
                if (bw != null) {
                    bw.append(oldMsg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(oldMsg);

                String newMsg = "  new[" + newResult[i].getTermList().toString() + "]";
                if (bw != null) {
                    bw.append(newMsg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(newMsg);

                different = true;
            }
            if (!oldResult[i].getPosList().equals(newResult[i].getPosList())) {
                String msg = "analyze result[posList] is different!!";
                if (bw != null) {
                    bw.append(msg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(msg);

                String oldMsg = "  old[" + oldResult[i].getPosList().toString() + "]";
                if (bw != null) {
                    bw.append(oldMsg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(oldMsg);

                String newMsg = "  new[" + newResult[i].getPosList().toString() + "]";
                if (bw != null) {
                    bw.append(newMsg);
                    bw.newLine();
                }
                if (printToConsole) System.out.println(newMsg);

                different = true;
            }
            break;
        }
        return different;
    }

    private static void printResults(int counter, WikipediaModel model,
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

    private static File[] getJarFiles(String path) {
        File file = new File(path);

        if (file.isDirectory()) {
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
            System.out.println("Using JAR file: " + file.getName());
            return new File[]{file};
        } else {
            throw new RuntimeException("Path is neither a file nor a directory: " + path);
        }
    }

    /**
     * Wiki-40B Parquet用のReadSupport実装
     */
    static class Wiki40bReadSupport extends ReadSupport<WikipediaModel> {

        @Override
        public ReadContext init(Configuration configuration, java.util.Map<String, String> keyValueMetaData,
                                MessageType fileSchema) {
            return new ReadContext(fileSchema);
        }

        @Override
        public RecordMaterializer<WikipediaModel> prepareForRead(Configuration configuration,
                                                                  java.util.Map<String, String> keyValueMetaData,
                                                                  MessageType fileSchema,
                                                                  ReadContext readContext) {
            return new Wiki40bRecordMaterializer(fileSchema);
        }
    }

    /**
     * Wiki-40B用のRecordMaterializer
     */
    static class Wiki40bRecordMaterializer extends RecordMaterializer<WikipediaModel> {
        private final Wiki40bGroupConverter root;

        public Wiki40bRecordMaterializer(MessageType schema) {
            this.root = new Wiki40bGroupConverter();
        }

        @Override
        public WikipediaModel getCurrentRecord() {
            return root.getCurrentRecord();
        }

        @Override
        public GroupConverter getRootConverter() {
            return root;
        }
    }

    /**
     * Wiki-40B用のGroupConverter
     */
    static class Wiki40bGroupConverter extends GroupConverter {
        private WikipediaModel current = new WikipediaModel();

        @Override
        public Converter getConverter(int fieldIndex) {
            return new PrimitiveConverter() {
                @Override
                public void addBinary(Binary value) {
                    // Try different decoding approaches
                    String str;
                    try {
                        // First, try UTF-8 decoding
                        byte[] bytes = value.getBytes();
                        str = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        // Fallback to default method
                        str = value.toStringUsingUTF8();
                    }

                    // フィールドインデックスに基づいてマッピング
                    // Wiki-40Bのスキーマ: wikidata_id, text, version_id
                    switch (fieldIndex) {
                        case 0: // wikidata_id
                            current.setId(str);
                            break;
                        case 1: // text
                            current.setText(str);
                            // textからタイトルを抽出（最初の行または先頭部分）
                            extractTitleFromText(str);
                            break;
                        case 2: // version_id
                            // version_idは使用しない
                            break;
                    }
                }
            };
        }

        private void extractTitleFromText(String text) {
            if (text != null && !text.isEmpty()) {
                // テキストの各行を確認して、最初の空でない行をタイトルとして使用
                String[] lines = text.split("\n");
                String title = "";

                for (String line : lines) {
                    String trimmedLine = line.trim();
                    // 空行や特殊マーカーをスキップして、最初の実質的な内容をタイトルとする
                    if (!trimmedLine.isEmpty() &&
                        !trimmedLine.equals("_START_ARTICLE_") &&
                        !trimmedLine.startsWith("_START_SECTION_") &&
                        !trimmedLine.startsWith("_START_PARAGRAPH_")) {
                        title = trimmedLine;
                        break;
                    }
                }

                // タイトルが見つからない場合は最初の非空行を使用
                if (title.isEmpty() && lines.length > 0) {
                    for (String line : lines) {
                        String trimmedLine = line.trim();
                        if (!trimmedLine.isEmpty()) {
                            title = trimmedLine;
                            break;
                        }
                    }
                }

                // タイトルが100文字を超える場合は切り詰める
                if (title.length() > 100) {
                    title = title.substring(0, 100);
                }
                current.setTitle(title);
            }
        }

        @Override
        public void start() {
            current = new WikipediaModel();
        }

        @Override
        public void end() {
            // 必要に応じて処理
        }

        public WikipediaModel getCurrentRecord() {
            return current;
        }
    }
}
