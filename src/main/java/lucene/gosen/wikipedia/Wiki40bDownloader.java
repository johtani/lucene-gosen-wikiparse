package lucene.gosen.wikipedia;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Hugging FaceのWiki-40B日本語データセットをダウンロードするクラス
 */
public class Wiki40bDownloader {

    private static final String BASE_URL = "https://huggingface.co/datasets/range3/wiki40b-ja/resolve/main/";
    private static final String DEFAULT_DEST_DIR = "./data/wiki40b-ja";

    // 利用可能なデータセット分割
    public enum Split {
        TRAIN("train.parquet"),
        VALIDATION("validation.parquet"),
        TEST("test.parquet");

        private final String fileName;

        Split(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }

        public String getUrl() {
            return BASE_URL + fileName;
        }
    }

    public static void main(String[] args) {
        String destDir = args.length > 0 ? args[0] : DEFAULT_DEST_DIR;
        String splitArg = args.length > 1 ? args[1].toUpperCase() : "ALL";

        try {
            if ("ALL".equals(splitArg)) {
                downloadAll(destDir);
            } else {
                Split split = Split.valueOf(splitArg);
                downloadSplit(split, destDir);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid split name. Use: TRAIN, VALIDATION, TEST, or ALL");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * すべてのデータセット分割をダウンロード
     */
    public static void downloadAll(String destDir) throws IOException {
        System.out.println("=== Downloading all Wiki-40B Japanese dataset splits ===");
        for (Split split : Split.values()) {
            downloadSplit(split, destDir);
        }
        System.out.println("=== All downloads completed! ===");
    }

    /**
     * 指定されたデータセット分割をダウンロード
     */
    public static void downloadSplit(Split split, String destDir) throws IOException {
        Path destPath = Paths.get(destDir);
        if (Files.notExists(destPath)) {
            Files.createDirectories(destPath);
            System.out.println("Created directory: " + destPath);
        }

        String url = split.getUrl();
        String destFile = destDir + "/" + split.getFileName();

        downloadFile(url, destFile);
    }

    /**
     * URLからファイルをダウンロード
     */
    private static void downloadFile(String urlString, String destString) throws IOException {
        Path destPath = Paths.get(destString);

        // 既に存在する場合はスキップ
        if (Files.exists(destPath)) {
            System.out.println("File already exists, skipping: " + destPath.getFileName());
            return;
        }

        System.out.println("Downloading from: " + urlString);
        System.out.println("To: " + destPath.toAbsolutePath());

        URL url = URI.create(urlString).toURL();
        try (InputStream in = new BufferedInputStream(url.openStream());
             FileOutputStream out = new FileOutputStream(destPath.toFile())) {

            byte[] dataBuffer = new byte[65536]; // 64KB buffer
            int bytesRead;
            long totalBytesRead = 0;
            long lastReportedTime = System.currentTimeMillis();

            while ((bytesRead = in.read(dataBuffer)) != -1) {
                out.write(dataBuffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                long currentTime = System.currentTimeMillis();
                if (currentTime - lastReportedTime > 5000) { // 5秒ごとに進捗表示
                    System.out.printf("Downloaded: %.2f MB%n", totalBytesRead / (1024.0 * 1024.0));
                    lastReportedTime = currentTime;
                }
            }
            System.out.printf("Download completed! Total size: %.2f MB%n", totalBytesRead / (1024.0 * 1024.0));
        }
    }
}
