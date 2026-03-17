package lucene.gosen.wikipedia;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.io.api.*;
import org.apache.parquet.schema.MessageType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Wiki-40B ParquetファイルをパースしてWikipediaModelに変換するクラス
 */
@Command(name = "parse-parquet", mixinStandardHelpOptions = true, version = "1.0",
        description = "Parse Wiki-40B Parquet file and compare analysis results between two versions")
public class Wiki40bParquetParser extends AbstractWikipediaParser implements Callable<Integer> {

    @Option(names = {"-o", "--old-jar"}, required = true,
            description = "Path to old JAR file or directory containing JAR files")
    private String oldJarPath;

    @Option(names = {"-n", "--new-jar"}, required = true,
            description = "Path to new JAR file or directory containing JAR files")
    private String newJarPath;

    @Option(names = {"-i", "--input"}, defaultValue = "./data/wiki40b-ja/train.parquet",
            description = "Parquet file path (default: ${DEFAULT-VALUE})")
    private String inputPath;

    @Option(names = {"-m", "--max-records"}, defaultValue = "-1",
            description = "Maximum number of records to process (default: all records)")
    private int maxRecordCount;

    @Option(names = {"-f", "--format"}, defaultValue = "text",
            description = "Report format: text, html, or both (default: ${DEFAULT-VALUE})")
    private String reportFormat;

    @Option(names = {"-w", "--workers"}, defaultValue = "1",
            description = "Number of worker threads for morphological analysis (default: ${DEFAULT-VALUE})")
    private int workerCount;

    @Option(names = {"-q", "--queue-size"}, defaultValue = "1000",
            description = "Maximum number of in-flight records for parallel processing (default: ${DEFAULT-VALUE})")
    private int queueSize;

    private ParquetReader<WikipediaModel> parquetReader;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Wiki40bParquetParser()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // Validate report format
        if (!reportFormat.equals("text") && !reportFormat.equals("html") && !reportFormat.equals("both")) {
            System.err.println("Error: report format must be 'text', 'html', or 'both', got: " + reportFormat);
            return 1;
        }

        // Validate max record count
        if (maxRecordCount < -1 || maxRecordCount == 0) {
            System.err.println("Error: max record count must be a positive number or -1 for unlimited");
            return 1;
        }
        if (workerCount <= 0) {
            System.err.println("Error: workers must be a positive number");
            return 1;
        }
        if (queueSize <= 0) {
            System.err.println("Error: queue-size must be a positive number");
            return 1;
        }

        // ParserConfigを構築
        ParserConfig config = ParserConfig.builder()
                .oldJarPath(oldJarPath)
                .newJarPath(newJarPath)
                .inputPath(inputPath)
                .maxRecordCount(maxRecordCount)
                .reportFormat(reportFormat)
                .workerCount(workerCount)
                .queueSize(queueSize)
                .build();

        // パーサーを実行
        execute(config);
        return 0;
    }

    @Override
    protected String getDataSourceType() {
        return "Parquet";
    }

    @Override
    protected Object initializeDataSource(ParserConfig config) throws Exception {
        try {
            ParquetFileValidator.assertReadableParquetFile(Paths.get(config.getInputPath()));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Invalid parquet input: " + config.getInputPath()
                            + ". The file may be incomplete or not parquet. "
                            + "Delete the file and re-download it with runWiki40bDownload.", e);
        }

        Configuration conf = new Configuration();
        Path path = new Path(config.getInputPath());
        parquetReader = ParquetReader.builder(new Wiki40bReadSupport(), path)
                .withConf(conf)
                .build();
        return parquetReader;
    }

    @Override
    protected WikipediaModel readNextModel(Object dataSource) throws Exception {
        return parquetReader.read();
    }

    @Override
    protected boolean shouldProcessModel(WikipediaModel model) {
        return model != null && model.getText() != null && !model.getText().isEmpty();
    }

    @Override
    protected void closeDataSource(Object dataSource) throws Exception {
        if (parquetReader != null) {
            parquetReader.close();
        }
    }

    @Override
    protected String getTextReportFileName() {
        return "diff_result_wiki40b.txt";
    }

    @Override
    protected String getHtmlReportFileName() {
        return "diff_result_wiki40b.html";
    }

    /**
     * Wiki-40B Parquet用のReadSupport実装
     */
    static class Wiki40bReadSupport extends ReadSupport<WikipediaModel> {

        @Override
        @SuppressWarnings("deprecation")
        public ReadContext init(Configuration configuration, java.util.Map<String, String> keyValueMetaData,
                                MessageType fileSchema) {
            return new ReadContext(fileSchema);
        }

        @Override
        public RecordMaterializer<WikipediaModel> prepareForRead(Configuration configuration,
                                                                 java.util.Map<String, String> keyValueMetaData,
                                                                 MessageType fileSchema,
                                                                 ReadContext readContext) {
            return new Wiki40bRecordMaterializer();
        }
    }

    /**
     * Wiki-40B用のRecordMaterializer
     */
    static class Wiki40bRecordMaterializer extends RecordMaterializer<WikipediaModel> {
        private final Wiki40bGroupConverter root;

        public Wiki40bRecordMaterializer() {
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
                if (title.isEmpty()) {
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
