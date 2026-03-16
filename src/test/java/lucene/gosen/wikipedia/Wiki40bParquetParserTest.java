package lucene.gosen.wikipedia;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.PrimitiveConverter;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class Wiki40bParquetParserTest {

    @Test
    void testResultSizeConstant() {
        assertEquals(2, Wiki40bParquetParser.RESULT_SIZE);
    }

    @Test
    void testWiki40bReadSupportExists() {
        // Wiki40bReadSupportクラスが正しくインスタンス化できることを確認
        assertDoesNotThrow(() -> {
            Wiki40bParquetParser.Wiki40bReadSupport readSupport =
                new Wiki40bParquetParser.Wiki40bReadSupport();
            assertNotNull(readSupport);
        });
    }

    @Test
    void testWiki40bGroupConverterExists() {
        // Wiki40bGroupConverterクラスが正しくインスタンス化できることを確認
        assertDoesNotThrow(() -> {
            Wiki40bParquetParser.Wiki40bGroupConverter groupConverter =
                new Wiki40bParquetParser.Wiki40bGroupConverter();
            assertNotNull(groupConverter);
            assertNotNull(groupConverter.getCurrentRecord());
        });
    }

    @Test
    void testWiki40bGroupConverterLifecycle() {
        Wiki40bParquetParser.Wiki40bGroupConverter converter =
            new Wiki40bParquetParser.Wiki40bGroupConverter();

        // start()を呼び出すと新しいWikipediaModelが作成される
        converter.start();
        WikipediaModel model1 = converter.getCurrentRecord();
        assertNotNull(model1);

        // もう一度start()を呼び出すと別のインスタンスが作成される
        converter.start();
        WikipediaModel model2 = converter.getCurrentRecord();
        assertNotNull(model2);
        assertNotSame(model1, model2);

        // end()は例外を投げない
        assertDoesNotThrow(() -> converter.end());
    }

    @Test
    void testConverterGetConverter() {
        Wiki40bParquetParser.Wiki40bGroupConverter converter =
            new Wiki40bParquetParser.Wiki40bGroupConverter();

        // 各フィールドインデックスに対してConverterが取得できることを確認
        assertNotNull(converter.getConverter(0)); // wikidata_id
        assertNotNull(converter.getConverter(1)); // text
        assertNotNull(converter.getConverter(2)); // version_id
    }

    @Test
    void testParsingTitleAndText() {
        Wiki40bParquetParser.Wiki40bGroupConverter converter =
            new Wiki40bParquetParser.Wiki40bGroupConverter();

        // テストデータの準備
        String testTitle = "テストタイトル";
        String testBody = "これはテスト本文です。";
        String testText = testTitle + "\n" + testBody;
        String testId = "Q123456";

        // パース処理をシミュレート
        converter.start();

        // wikidata_idをセット（fieldIndex=0）
        ((PrimitiveConverter) converter.getConverter(0)).addBinary(
            Binary.fromString(testId));

        // textをセット（fieldIndex=1）
        ((PrimitiveConverter) converter.getConverter(1)).addBinary(
            Binary.fromString(testText));

        converter.end();

        // パース結果の確認
        WikipediaModel model = converter.getCurrentRecord();
        assertNotNull(model);
        assertEquals(testId, model.getId());
        assertEquals(testTitle, model.getTitle());
        assertEquals(testText, model.getText());
    }

    @Test
    void testParsingLongTitle() {
        Wiki40bParquetParser.Wiki40bGroupConverter converter =
            new Wiki40bParquetParser.Wiki40bGroupConverter();

        // 100文字を超える長いタイトルをテスト
        String longTitle = "あ".repeat(150);
        String testBody = "本文";
        String testText = longTitle + "\n" + testBody;

        converter.start();
        ((PrimitiveConverter) converter.getConverter(1)).addBinary(
            Binary.fromString(testText));
        converter.end();

        WikipediaModel model = converter.getCurrentRecord();
        assertNotNull(model);
        // タイトルが100文字に切り詰められることを確認
        assertEquals(100, model.getTitle().length());
        assertEquals(longTitle.substring(0, 100), model.getTitle());
        assertEquals(testText, model.getText());
    }

    @Test
    void testParsingEmptyText() {
        Wiki40bParquetParser.Wiki40bGroupConverter converter =
            new Wiki40bParquetParser.Wiki40bGroupConverter();

        converter.start();
        ((PrimitiveConverter) converter.getConverter(1)).addBinary(
            Binary.fromString(""));
        converter.end();

        WikipediaModel model = converter.getCurrentRecord();
        assertNotNull(model);
        assertEquals("", model.getText());
    }

    @Test
    void testReadActualParquetFile() throws Exception {
        // テスト用のParquetファイルパス
        String testParquetPath = "./data/wiki40b-ja/test.parquet";
        File testFile = new File(testParquetPath);

        // ファイルが存在しない場合はスキップ
        if (!testFile.exists()) {
            System.out.println("Test parquet file not found, skipping test: " + testParquetPath);
            return;
        }

        Configuration conf = new Configuration();
        Path path = new Path(testParquetPath);

        // 最初の3レコードを読み込んでパース結果を検証
        try (ParquetReader<WikipediaModel> reader = ParquetReader.builder(
                new Wiki40bParquetParser.Wiki40bReadSupport(), path)
                .withConf(conf)
                .build()) {

            int recordCount = 0;
            int maxRecords = 3;
            WikipediaModel model;

            while ((model = reader.read()) != null && recordCount < maxRecords) {
                // 基本的な検証
                assertNotNull(model, "Model should not be null");

                System.out.println("\n=== Record #" + (recordCount + 1) + " ===");
                System.out.println("ID: " + (model.getId() != null ? model.getId() : "(null)"));
                System.out.println("Title: " + (model.getTitle() != null ? "\"" + model.getTitle() + "\"" : "(null)"));
                System.out.println("Text: " + (model.getText() != null ? "\"" + model.getText() + "\"" : "(null)"));
                System.out.println("Text length: " + (model.getText() != null ? model.getText().length() : 0));

                // テキストが空でない場合のみ、詳細な検証を行う
                if (model.getText() != null && !model.getText().isEmpty()) {
                    // IDが設定されていることを確認
                    assertNotNull(model.getId(), "ID should not be null");

                    // タイトルが設定されていることを確認
                    assertNotNull(model.getTitle(), "Title should not be null when text is not empty");
                    assertFalse(model.getTitle().isEmpty(), "Title should not be empty when text is not empty");

                    // タイトルの長さが100文字以下であることを確認
                    assertTrue(model.getTitle().length() <= 100,
                        "Title length should be <= 100, but was " + model.getTitle().length());

                    recordCount++;
                } else {
                    System.out.println("(Skipping record with empty text)");
                }
            }

            // 少なくとも1レコードは読み込めたことを確認
            assertTrue(recordCount > 0, "Should read at least one record from parquet file");
            System.out.println("\nSuccessfully read " + recordCount + " records from parquet file");
        }
    }
}
