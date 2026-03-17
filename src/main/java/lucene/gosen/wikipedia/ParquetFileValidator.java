package lucene.gosen.wikipedia;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parquetファイルの妥当性を簡易検証するユーティリティ.
 */
public final class ParquetFileValidator {

    private static final String PARQUET_MAGIC = "PAR1";
    private static final int MAGIC_SIZE = 4;
    private static final int MIN_PARQUET_SIZE = 8;

    private ParquetFileValidator() {
    }

    /**
     * 入力パスが読み取り可能で、Parquetのマジックバイトを持つことを検証する.
     *
     * @throws IOException 検証に失敗した場合
     */
    public static void assertReadableParquetFile(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Input parquet path is null");
        }
        if (!Files.exists(path)) {
            throw new IOException("Input parquet file does not exist: " + path.toAbsolutePath());
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("Input parquet path is not a regular file: " + path.toAbsolutePath());
        }
        if (!Files.isReadable(path)) {
            throw new IOException("Input parquet file is not readable: " + path.toAbsolutePath());
        }

        long size = Files.size(path);
        if (size < MIN_PARQUET_SIZE) {
            throw new IOException("Input parquet file is too small (" + size + " bytes): " + path.toAbsolutePath());
        }

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            byte[] header = new byte[MAGIC_SIZE];
            raf.readFully(header);

            raf.seek(size - MAGIC_SIZE);
            byte[] footer = new byte[MAGIC_SIZE];
            raf.readFully(footer);

            String headerMagic = new String(header, StandardCharsets.US_ASCII);
            String footerMagic = new String(footer, StandardCharsets.US_ASCII);

            if (!PARQUET_MAGIC.equals(headerMagic) || !PARQUET_MAGIC.equals(footerMagic)) {
                throw new IOException(
                        "File is not a valid Parquet file (expected PAR1 magic at header/footer): "
                                + path.toAbsolutePath());
            }
        }
    }

    /**
     * Parquetとして読める可能性が高いかを返す.
     */
    public static boolean isLikelyParquetFile(Path path) {
        try {
            assertReadableParquetFile(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
