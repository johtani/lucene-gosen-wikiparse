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
 * Wikipediaのダンプファイルをダウンロードするクラス
 */
public class WikipediaDownloader {

    private static final String DEFAULT_URL = "https://dumps.wikimedia.org/jawiki/latest/jawiki-latest-pages-articles.xml.bz2";
    private static final String DEFAULT_DEST = "./data/jawiki-latest-pages-articles.xml.bz2";

    public static void main(String[] args) {
        String urlString = args.length > 0 ? args[0] : DEFAULT_URL;
        String destString = args.length > 1 ? args[1] : DEFAULT_DEST;

        try {
            downloadFile(urlString, destString);
        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void downloadFile(String urlString, String destString) throws IOException {
        Path destPath = Paths.get(destString);
        Path parentDir = destPath.getParent();
        if (parentDir != null && Files.notExists(parentDir)) {
            Files.createDirectories(parentDir);
            System.out.println("Created directory: " + parentDir);
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
