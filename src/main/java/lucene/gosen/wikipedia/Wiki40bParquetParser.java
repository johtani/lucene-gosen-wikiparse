package lucene.gosen.wikipedia;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.io.api.*;
import org.apache.parquet.schema.MessageType;

import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * Wiki-40B ParquetファイルをパースしてWikipediaModelに変換するクラス
 */
public class Wiki40bParquetParser extends AbstractWikipediaParser {

    private ParquetReader<WikipediaModel> parquetReader;
    private BufferedWriter bufferedWriter;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("arg[0] is old jar or directory, arg[1] is new jar or directory, [arg[2] is parquet file (default: ./data/wiki40b-ja/train.parquet)], [arg[3] is max record count (optional, default: all records)], [arg[4] is report format (text|html|both, default: text)]");
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
        String reportFormat = args.length >= 5 ? args[4].toLowerCase() : "text";
        if (!reportFormat.equals("text") && !reportFormat.equals("html") && !reportFormat.equals("both")) {
            System.err.println("Error: report format must be 'text', 'html', or 'both', got: " + reportFormat);
            System.exit(-1);
        }

        // ParserConfigを構築
        ParserConfig config = ParserConfig.builder()
                .oldJarPath(args[0])
                .newJarPath(args[1])
                .inputPath(parquetPath)
                .maxRecordCount(maxRecordCount)
                .reportFormat(reportFormat)
                .build();

        // パーサーを実行
        Wiki40bParquetParser parser = new Wiki40bParquetParser();
        parser.execute(config);
    }

    @Override
    protected String getDataSourceType() {
        return "Parquet";
    }

    @Override
    protected Object initializeDataSource(ParserConfig config) throws Exception {
        Configuration conf = new Configuration();
        Path path = new Path(config.getInputPath());
        parquetReader = ParquetReader.builder(new Wiki40bReadSupport(), path)
                .withConf(conf)
                .build();

        // テキストレポート用のBufferedWriterを初期化（互換性のため）
        if (config.getReportFormat().equals("text") || config.getReportFormat().equals("both")) {
            bufferedWriter = new BufferedWriter(new FileWriter("diff_result_wiki40b.txt"));
        }

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
        if (bufferedWriter != null) {
            bufferedWriter.close();
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
