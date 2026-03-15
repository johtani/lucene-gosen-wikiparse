package lucene.gosen.wikipedia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Wiki40bDownloaderTest {

    @Test
    void testSplitEnum() {
        assertEquals("train.parquet", Wiki40bDownloader.Split.TRAIN.getFileName());
        assertEquals("validation.parquet", Wiki40bDownloader.Split.VALIDATION.getFileName());
        assertEquals("test.parquet", Wiki40bDownloader.Split.TEST.getFileName());

        assertTrue(Wiki40bDownloader.Split.TRAIN.getUrl().contains("train.parquet"));
        assertTrue(Wiki40bDownloader.Split.VALIDATION.getUrl().contains("validation.parquet"));
        assertTrue(Wiki40bDownloader.Split.TEST.getUrl().contains("test.parquet"));
    }

    @Test
    void testDownloadSplitCreatesDirectory(@TempDir Path tempDir) throws IOException {
        String destDir = tempDir.resolve("wiki40b-test").toString();

        // ディレクトリが存在しないことを確認
        assertFalse(Files.exists(Path.of(destDir)));

        // Note: 実際のダウンロードはテストしない（時間がかかるため）
        // ディレクトリ作成のロジックのみテスト
        try {
            Wiki40bDownloader.downloadSplit(Wiki40bDownloader.Split.TEST, destDir);
        } catch (IOException e) {
            // ネットワークエラーやタイムアウトは無視
            // ディレクトリが作成されていればOK
        }

        // ディレクトリが作成されたことを確認
        assertTrue(Files.exists(Path.of(destDir)));
        assertTrue(Files.isDirectory(Path.of(destDir)));
    }

    @Test
    void testSplitValueOf() {
        assertEquals(Wiki40bDownloader.Split.TRAIN, Wiki40bDownloader.Split.valueOf("TRAIN"));
        assertEquals(Wiki40bDownloader.Split.VALIDATION, Wiki40bDownloader.Split.valueOf("VALIDATION"));
        assertEquals(Wiki40bDownloader.Split.TEST, Wiki40bDownloader.Split.valueOf("TEST"));

        assertThrows(IllegalArgumentException.class, () -> {
            Wiki40bDownloader.Split.valueOf("INVALID");
        });
    }
}
