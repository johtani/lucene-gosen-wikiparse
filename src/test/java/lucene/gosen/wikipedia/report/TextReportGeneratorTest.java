package lucene.gosen.wikipedia.report;

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.wikipedia.WikipediaModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TextReportGeneratorTest {

    @TempDir
    Path tempDir;

    private Path outputFile;
    private TextReportGenerator generator;
    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void setUp() throws IOException {
        outputFile = tempDir.resolve("test_report.txt");
        generator = new TextReportGenerator(outputFile.toString());
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
    }

    @AfterEach
    void tearDown() throws IOException {
        System.setOut(originalOut);
        generator.close();
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

    // --- バグ修正のテスト ---

    @Test
    void testCostDiffOutputsTitle() throws IOException {
        WikipediaModel model = createModel("テスト記事", "本文テキスト");
        AnalyzeResult[] oldResults = createResults(100, "単語", "名詞");
        AnalyzeResult[] newResults = createResults(200, "単語", "名詞"); // costのみ異なる

        generator.addDiffResult(model, oldResults, newResults, true, false);
        generator.flush();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("Title: テスト記事"), "cost差分時にタイトルが出力されること");
        assertTrue(content.contains("analyze result[cost] is different!!"), "cost差分メッセージが出力されること");
        assertTrue(content.contains("old[100]"), "old cost値が出力されること");
        assertTrue(content.contains("new[200]"), "new cost値が出力されること");
    }

    @Test
    void testTermListDiffOutputsTitle() throws IOException {
        // バグ修正確認: termList差分のみの場合もタイトルが出力されること
        WikipediaModel model = createModel("テスト記事", "本文テキスト");
        AnalyzeResult[] oldResults = createResults(100, "旧単語", "名詞");
        AnalyzeResult[] newResults = createResults(100, "新単語", "名詞"); // termListのみ異なる

        generator.addDiffResult(model, oldResults, newResults, true, false);
        generator.flush();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("Title: テスト記事"), "termList差分時もタイトルが出力されること（バグ修正確認）");
        assertTrue(content.contains("analyze result[termList] is different!!"), "termList差分メッセージが出力されること");
    }

    @Test
    void testPosListDiffOutputsTitle() throws IOException {
        // バグ修正確認: posList差分のみの場合もタイトルが出力されること
        WikipediaModel model = createModel("テスト記事", "本文テキスト");
        AnalyzeResult[] oldResults = createResults(100, "単語", "名詞");
        AnalyzeResult[] newResults = createResults(100, "単語", "動詞"); // posListのみ異なる

        generator.addDiffResult(model, oldResults, newResults, true, false);
        generator.flush();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("Title: テスト記事"), "posList差分時もタイトルが出力されること（バグ修正確認）");
        assertTrue(content.contains("analyze result[posList] is different!!"), "posList差分メッセージが出力されること");
    }

    @Test
    void testPrintToConsole_false_noStdout() throws IOException {
        // バグ修正確認: printToConsole=false の場合、標準出力に何も出力されないこと
        WikipediaModel model = createModel("テスト記事", "本文テキスト");
        AnalyzeResult[] oldResults = createResults(100, "単語", "名詞");
        AnalyzeResult[] newResults = createResults(200, "単語", "名詞");

        generator.addDiffResult(model, oldResults, newResults, true, false);
        generator.flush();

        String stdout = capturedOut.toString();
        assertTrue(stdout.isEmpty(), "printToConsole=false の時は標準出力に何も出力されないこと（バグ修正確認）");
    }

    @Test
    void testPrintToConsole_true_outputsTitleAndText() throws IOException {
        // printToConsole=true の場合、タイトルと本文が標準出力に出力されること
        WikipediaModel model = createModel("テスト記事", "本文テキスト");
        AnalyzeResult[] oldResults = createResults(100, "単語", "名詞");
        AnalyzeResult[] newResults = createResults(200, "単語", "名詞");

        generator.addDiffResult(model, oldResults, newResults, true, true);
        generator.flush();

        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("Title: テスト記事"), "printToConsole=true の時にタイトルが標準出力に出ること");
        assertTrue(stdout.contains("本文テキスト"), "printToConsole=true の時に本文が標準出力に出ること");
    }

    @Test
    void testNoDiff_nothingWritten() throws IOException {
        WikipediaModel model = createModel("テスト記事", "本文テキスト");
        AnalyzeResult[] results = createResults(100, "単語", "名詞");

        generator.addDiffResult(model, results, results, false, false);
        generator.flush();

        String content = Files.readString(outputFile);
        assertTrue(content.isEmpty(), "差分がない場合はファイルに何も書かれないこと");
    }

    @Test
    void testTitleAppearsBeforeDiffDetail() throws IOException {
        // タイトルが差分詳細より前に出力されること
        WikipediaModel model = createModel("テスト記事", "本文テキスト");
        AnalyzeResult[] oldResults = createResults(100, "旧単語", "名詞");
        AnalyzeResult[] newResults = createResults(200, "新単語", "名詞");

        generator.addDiffResult(model, oldResults, newResults, true, false);
        generator.flush();

        String content = Files.readString(outputFile);
        int titlePos = content.indexOf("Title: テスト記事");
        int costPos = content.indexOf("analyze result[cost] is different!!");
        assertTrue(titlePos < costPos, "タイトルが差分詳細より前に出力されること");
    }

    @Test
    void testSummaryIncludesFailedCounter() throws IOException {
        ExecutionInfo info = new ExecutionInfo();
        info.setOldJarPath("old.jar");
        info.setNewJarPath("new.jar");
        info.setTotalProcessed(10);
        info.setDifferenceCount(3);
        info.setSkippedCount(2);
        info.setFailedCount(1);
        generator.setExecutionInfo(info);

        WikipediaModel model = createModel("テスト記事", "本文テキスト");
        AnalyzeResult[] oldResults = createResults(100, "旧単語", "名詞");
        AnalyzeResult[] newResults = createResults(200, "新単語", "名詞");
        generator.addDiffResult(model, oldResults, newResults, true, false);
        generator.generateReport(outputFile.toString());
        generator.flush();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("failedCounter: 1"), "サマリーにfailedCounterが出力されること");
    }
}
