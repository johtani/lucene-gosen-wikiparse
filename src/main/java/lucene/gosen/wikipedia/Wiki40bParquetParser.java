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

/**
 * Wiki-40B ParquetファイルをパースしてWikipediaModelに変換するクラス
 */
public class Wiki40bParquetParser {

    public static final int RESULT_SIZE = 2;

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        if (args.length < 2) {
            System.out.println("arg[0] is old jar or directory, arg[1] is new jar or directory, [arg[2] is parquet file (default: ./data/wiki40b-ja/train.parquet)]");
            System.exit(-1);
        }

        String parquetPath = args.length >= 3 ? args[2] : "./data/wiki40b-ja/train.parquet";

        BufferedWriter bw = new BufferedWriter(new FileWriter("diff_result_wiki40b.txt"));

        System.out.println("start :: " + new Date(start));
        ComponentContainer oldJarContainer = new ComponentContainer(getJarFiles(args[0]));
        ComponentContainer newJarContainer = new ComponentContainer(getJarFiles(args[1]));

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
            WikipediaModel model;

            while ((model = reader.read()) != null) {
                if (model.getText() != null && !model.getText().isEmpty()) {
                    oldModelAnalyzer.analyze(model, oldJarContainer, oldResult);
                    newModelAnalyzer.analyze(model, newJarContainer, newResult);

                    if (compareResult(bw, model, oldResult, newResult)) {
                        falseCounter++;
                    }

                    if (counter % 1000 == 0) {
                        System.out.println("success count:" + counter);
                        bw.flush();
                    }
                    counter++;
                }
            }

            bw.close();
            System.out.println("falseCounter:" + falseCounter);
            System.out.println((System.currentTimeMillis() - start) + "msec");
        }
    }

    private static boolean compareResult(BufferedWriter bw, WikipediaModel model,
                                         AnalyzeResult[] oldResult, AnalyzeResult[] newResult) throws IOException {
        boolean different = false;

        // size check
        for (int i = 0; i < RESULT_SIZE; i++) {
            if (oldResult[i].getTotalCost() != newResult[i].getTotalCost()) {
                bw.append("analyze result[cost] is different!!");
                bw.newLine();
                bw.append("  old[" + oldResult[i].getTotalCost() + "]");
                bw.newLine();
                bw.append("  new[" + newResult[i].getTotalCost() + "]");
                bw.newLine();
                different = true;
            }
            if (different) {
                if (i == 0) {
                    bw.append(model.title);
                }
            }
            if (!oldResult[i].getTermList().equals(newResult[i].getTermList())) {
                bw.append("analyze result[termList] is different!!");
                bw.newLine();
                bw.append("  old[" + oldResult[i].getTermList().toString() + "]");
                bw.newLine();
                bw.append("  new[" + newResult[i].getTermList().toString() + "]");
                bw.newLine();
                different = true;
            }
            if (!oldResult[i].getPosList().equals(newResult[i].getPosList())) {
                bw.append("analyze result[posList] is different!!");
                bw.newLine();
                bw.append("  old[" + oldResult[i].getPosList().toString() + "]");
                bw.newLine();
                bw.append("  new[" + newResult[i].getPosList().toString() + "]");
                bw.newLine();
                different = true;
            }
            break;
        }
        return different;
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
                    String str = value.toStringUsingUTF8();
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
                // テキストの最初の行または最初の100文字をタイトルとして使用
                String[] lines = text.split("\n", 2);
                String title = lines[0].trim();
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
