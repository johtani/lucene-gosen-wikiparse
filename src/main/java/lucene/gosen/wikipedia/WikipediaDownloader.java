package lucene.gosen.wikipedia;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileOutputStream;
import java.io.IOException;
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
        DownloadUtils.ensureParentDirectory(destPath);

        // SKIP_IF_VALID: サイズ0より大きい既存ファイルは有効とみなして再ダウンロードしない
        if (Files.exists(destPath)) {
            if (Files.size(destPath) > 0) {
                System.out.println("Valid file already exists, skipping: " + destPath.getFileName());
                return;
            }
            System.out.println("Existing file is empty, re-downloading: " + destPath.getFileName());
            Files.delete(destPath);
        }

        System.out.println("Downloading from: " + urlString);
        System.out.println("To: " + destPath.toAbsolutePath());

        URL url = URI.create(urlString).toURL();
        try (var in = url.openStream();
             FileOutputStream out = new FileOutputStream(destPath.toFile())) {
            DownloadUtils.copyWithProgress(in, out);
        }
    }
}
