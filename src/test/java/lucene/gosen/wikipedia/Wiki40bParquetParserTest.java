package lucene.gosen.wikipedia;

import org.junit.jupiter.api.Test;

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
}
