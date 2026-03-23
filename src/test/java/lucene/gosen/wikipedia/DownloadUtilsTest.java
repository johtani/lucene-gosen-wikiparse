package lucene.gosen.wikipedia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadUtilsTest {

    @Test
    void testEnsureDirectoryCreatesMissingDirectory(@TempDir Path tempDir) throws IOException {
        Path missing = tempDir.resolve("level1/level2");
        DownloadUtils.ensureDirectory(missing);
        assertTrue(Files.exists(missing));
        assertTrue(Files.isDirectory(missing));
    }

    @Test
    void testEnsureParentDirectoryCreatesMissingParent(@TempDir Path tempDir) throws IOException {
        Path filePath = tempDir.resolve("a/b/c/file.txt");
        DownloadUtils.ensureParentDirectory(filePath);
        assertTrue(Files.exists(filePath.getParent()));
        assertTrue(Files.isDirectory(filePath.getParent()));
    }

    @Test
    void testCopyWithProgressCopiesAllBytes() throws IOException {
        byte[] source = "copy-test-data".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(source);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long copied = DownloadUtils.copyWithProgress(in, out);

        assertEquals(source.length, copied);
        assertArrayEquals(source, out.toByteArray());
    }
}
