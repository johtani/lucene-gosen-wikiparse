package lucene.gosen.wikipedia;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Wikipediaのダンプファイルをダウンロードするクラス
 */
@Command(name = "download-wikipedia", mixinStandardHelpOptions = true, version = "1.0",
        description = "Download Wikipedia dump file from Wikimedia")
public class WikipediaDownloader implements Callable<Integer> {

    @Option(names = {"-u", "--url"},
            defaultValue = "https://dumps.wikimedia.org/jawiki/latest/jawiki-latest-pages-articles.xml.bz2",
            description = "URL to download from (default: ${DEFAULT-VALUE})")
    private String url;

    @Option(names = {"-d", "--destination"},
            defaultValue = "./data/jawiki-latest-pages-articles.xml.bz2",
            description = "Destination file path (default: ${DEFAULT-VALUE})")
    private String destination;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new WikipediaDownloader()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            downloadFile(url, destination);
            return 0;
        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
            e.printStackTrace();
            return 1;
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
