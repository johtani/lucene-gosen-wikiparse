package lucene.gosen.wikipedia.report;

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.wikipedia.WikipediaModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlReportGeneratorTest {

    @TempDir
    Path tempDir;

    private HtmlReportGenerator generator;
    private Path outputFile;

    @BeforeEach
    void setUp() {
        generator = new HtmlReportGenerator();
        outputFile = tempDir.resolve("report.html");

        ExecutionInfo info = new ExecutionInfo();
        info.setOldJarPath("old.jar");
        info.setNewJarPath("new.jar");
        info.setDataSourceType("XML");
        info.setDataSourcePath("input.xml");
        info.setTotalProcessed(1);
        info.setDifferenceCount(1);
        info.setSkippedCount(0);
        generator.setExecutionInfo(info);
    }

    @AfterEach
    void tearDown() throws IOException {
        generator.close();
    }

    @Test
    void generatesDiffRowsWithoutKeepingAllRecordsInMemory() throws IOException {
        WikipediaModel model = createModel("記事タイトル", "本文テキスト");
        AnalyzeResult[] oldResult = createResults(100, "旧単語", "名詞");
        AnalyzeResult[] newResult = createResults(200, "新単語", "名詞");

        generator.addDiffResult(model, oldResult, newResult, true, false);
        generator.generateReport(outputFile.toString());

        String html = Files.readString(outputFile);
        assertTrue(html.contains("差分詳細 (1件)"));
        assertTrue(html.contains("記事タイトル"));
        assertTrue(html.contains("diff-type cost"));
    }

    @Test
    void generatesNoDiffMessageWhenNoDifference() throws IOException {
        WikipediaModel model = createModel("記事タイトル", "本文テキスト");
        AnalyzeResult[] result = createResults(100, "単語", "名詞");

        generator.addDiffResult(model, result, result, false, false);
        generator.generateReport(outputFile.toString());

        String html = Files.readString(outputFile);
        assertTrue(html.contains("差分詳細 (0件)"));
        assertTrue(html.contains("差分はありません。"));
    }

    @Test
    void flushWritesIntermediateFileAndCloseCleansUp() throws Exception {
        WikipediaModel model = createModel("記事タイトル", "本文テキスト");
        AnalyzeResult[] oldResult = createResults(100, "旧単語", "名詞");
        AnalyzeResult[] newResult = createResults(200, "新単語", "名詞");

        generator.addDiffResult(model, oldResult, newResult, true, false);
        generator.flush();

        Path tempFile = readTempFilePath(generator);
        assertNotNull(tempFile);
        assertTrue(Files.exists(tempFile));
        assertTrue(Files.size(tempFile) > 0);

        generator.close();
        assertFalse(Files.exists(tempFile));
    }

    private WikipediaModel createModel(String title, String text) {
        WikipediaModel model = new WikipediaModel();
        model.setTitle(title);
        model.setText(text);
        return model;
    }

    private AnalyzeResult[] createResults(int cost, String term, String pos) {
        AnalyzeResult[] results = new AnalyzeResult[2];
        for (int i = 0; i < 2; i++) {
            results[i] = new AnalyzeResult();
            results[i].addCost(cost);
            results[i].addTerm(term);
            results[i].addPos(pos);
        }
        return results;
    }

    private Path readTempFilePath(HtmlReportGenerator generator) throws Exception {
        Field field = HtmlReportGenerator.class.getDeclaredField("diffTempFile");
        field.setAccessible(true);
        return (Path) field.get(generator);
    }
}
