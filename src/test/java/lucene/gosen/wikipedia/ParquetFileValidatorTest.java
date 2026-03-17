package lucene.gosen.wikipedia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParquetFileValidatorTest {

    @Test
    void testAssertReadableParquetFileWithValidMagic(@TempDir Path tempDir) throws IOException {
        Path parquet = tempDir.resolve("valid.parquet");
        byte[] content = "PAR1payloadPAR1".getBytes(StandardCharsets.US_ASCII);
        Files.write(parquet, content);

        assertDoesNotThrow(() -> ParquetFileValidator.assertReadableParquetFile(parquet));
        assertTrue(ParquetFileValidator.isLikelyParquetFile(parquet));
    }

    @Test
    void testAssertReadableParquetFileWithInvalidMagic(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("broken.parquet");
        Files.write(broken, "<html>not parquet</html>".getBytes(StandardCharsets.US_ASCII));

        assertThrows(IOException.class, () -> ParquetFileValidator.assertReadableParquetFile(broken));
        assertFalse(ParquetFileValidator.isLikelyParquetFile(broken));
    }
}
