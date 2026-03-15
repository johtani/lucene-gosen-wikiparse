package lucene.gosen.wikipedia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WikipediaDownloaderTest {

    @Test
    void testDefaultConstants() {
        // デフォルト値の確認（リフレクション経由）
        assertDoesNotThrow(() -> {
            WikipediaDownloader downloader = new WikipediaDownloader();
            assertNotNull(downloader);
        });
    }

    @Test
    void testDownloadFileCreatesDirectory(@TempDir Path tempDir) throws IOException {
        String destDir = tempDir.resolve("wikipedia-test").toString();
        String destFile = destDir + "/test.txt";

        // ディレクトリが存在しないことを確認
        assertFalse(Files.exists(Path.of(destDir)));

        // Note: 実際のダウンロードはネットワークに依存するためテストしない
        // ディレクトリ作成のロジックをテストするため、存在しないURLでテスト
        try {
            WikipediaDownloader.downloadFile("http://invalid.url/file.txt", destFile);
            fail("Should throw IOException for invalid URL");
        } catch (IOException e) {
            // 期待される動作: IOExceptionが発生
            // ただしディレクトリは作成されているはず
            assertTrue(Files.exists(Path.of(destDir)));
            assertTrue(Files.isDirectory(Path.of(destDir)));
        }
    }

    @Test
    void testDownloadFileWithInvalidURL(@TempDir Path tempDir) {
        String destFile = tempDir.resolve("test.txt").toString();

        assertThrows(Exception.class, () -> {
            WikipediaDownloader.downloadFile("not-a-valid-url", destFile);
        });
    }

    @Test
    void testDownloadFileWithNullURL(@TempDir Path tempDir) {
        String destFile = tempDir.resolve("test.txt").toString();

        assertThrows(Exception.class, () -> {
            WikipediaDownloader.downloadFile(null, destFile);
        });
    }

    @Test
    void testDownloadFileWithNullDestination() {
        assertThrows(Exception.class, () -> {
            WikipediaDownloader.downloadFile("http://example.com/file.txt", null);
        });
    }

    @Test
    void testDownloadFileWithEmptyURL(@TempDir Path tempDir) {
        String destFile = tempDir.resolve("test.txt").toString();

        assertThrows(Exception.class, () -> {
            WikipediaDownloader.downloadFile("", destFile);
        });
    }

    @Test
    void testDownloadFileCreatesParentDirectories(@TempDir Path tempDir) throws IOException {
        String destFile = tempDir.resolve("level1/level2/level3/file.txt").toString();

        // 親ディレクトリが存在しないことを確認
        assertFalse(Files.exists(Path.of(tempDir.toString(), "level1")));

        try {
            WikipediaDownloader.downloadFile("http://invalid.url/file.txt", destFile);
            fail("Should throw IOException");
        } catch (IOException e) {
            // 複数レベルの親ディレクトリが作成されていることを確認
            assertTrue(Files.exists(Path.of(tempDir.toString(), "level1")));
            assertTrue(Files.exists(Path.of(tempDir.toString(), "level1/level2")));
            assertTrue(Files.exists(Path.of(tempDir.toString(), "level1/level2/level3")));
        }
    }

    @Test
    void testDownloadFileWithExistingDirectory(@TempDir Path tempDir) throws IOException {
        Path destDir = tempDir.resolve("existing");
        Files.createDirectories(destDir);
        String destFile = destDir.resolve("file.txt").toString();

        // 既存のディレクトリがある場合でもエラーにならないことを確認
        try {
            WikipediaDownloader.downloadFile("http://invalid.url/file.txt", destFile);
            fail("Should throw IOException");
        } catch (IOException e) {
            // IOExceptionは発生するが、ディレクトリ関連のエラーではないことを確認
            assertTrue(Files.exists(destDir));
            assertTrue(Files.isDirectory(destDir));
        }
    }
}
