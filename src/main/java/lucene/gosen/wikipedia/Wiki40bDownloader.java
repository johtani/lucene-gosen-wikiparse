package lucene.gosen.wikipedia;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;

/**
 * Hugging FaceのWiki-40B日本語データセットをダウンロードするクラス
 */
@Command(name = "download-wiki40b", mixinStandardHelpOptions = true, version = "1.0",
        description = "Download Wiki-40B Japanese dataset from Hugging Face")
public class Wiki40bDownloader implements Callable<Integer> {

    private static final String BASE_URL = "https://huggingface.co/datasets/range3/wiki40b-ja/resolve/main/";

    @Option(names = {"-d", "--destination"}, defaultValue = "./data/wiki40b-ja",
            description = "Destination directory (default: ${DEFAULT-VALUE})")
    private String destination;

    @Option(names = {"-s", "--split"}, defaultValue = "ALL",
            description = "Dataset split: TRAIN, VALIDATION, TEST, or ALL (default: ${DEFAULT-VALUE})")
    private String split;

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
        int exitCode = new CommandLine(new Wiki40bDownloader()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            String splitUpper = split.toUpperCase();
            if ("ALL".equals(splitUpper)) {
                downloadAll(destination);
            } else {
                Split splitEnum = Split.valueOf(splitUpper);
                downloadSplit(splitEnum, destination);
            }
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid split name. Use: TRAIN, VALIDATION, TEST, or ALL");
            return 1;
        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
            e.printStackTrace();
            return 1;
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
        Path tempPath = Paths.get(destString + ".part");

        // 既存ファイルが有効なParquetならスキップ
        if (Files.exists(destPath)) {
            if (ParquetFileValidator.isLikelyParquetFile(destPath)) {
                System.out.println("Valid parquet file already exists, skipping: " + destPath.getFileName());
                return;
            }
            System.out.println("Existing file is invalid parquet, re-downloading: " + destPath.getFileName());
            Files.delete(destPath);
        }

        if (Files.exists(tempPath)) {
            Files.delete(tempPath);
        }

        System.out.println("Downloading from: " + urlString);
        System.out.println("To: " + destPath.toAbsolutePath());

        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(300_000);
        connection.setRequestMethod("GET");
        connection.connect();
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Failed to download file. HTTP status: " + status + " from " + urlString);
        }

        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream out = new FileOutputStream(tempPath.toFile())) {

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
        } catch (FileNotFoundException e) {
            throw new IOException("Download returned no file content: " + urlString, e);
        } finally {
            connection.disconnect();
        }

        ParquetFileValidator.assertReadableParquetFile(tempPath);
        try {
            Files.move(tempPath, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, destPath, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Saved validated parquet file: " + destPath.toAbsolutePath());
    }
}
