package lucene.gosen.wikipedia;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class DownloadUtils {

    private static final int BUFFER_SIZE = 65536;
    private static final long PROGRESS_INTERVAL_MILLIS = 5000L;

    private DownloadUtils() {
    }

    static void ensureDirectory(Path directory) throws IOException {
        if (directory != null && Files.notExists(directory)) {
            Files.createDirectories(directory);
            System.out.println("Created directory: " + directory);
        }
    }

    static void ensureParentDirectory(Path filePath) throws IOException {
        ensureDirectory(filePath.getParent());
    }

    static long copyWithProgress(InputStream in, OutputStream out) throws IOException {
        byte[] dataBuffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0L;
        long lastReportedTime = System.currentTimeMillis();

        while ((bytesRead = in.read(dataBuffer)) != -1) {
            out.write(dataBuffer, 0, bytesRead);
            totalBytesRead += bytesRead;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastReportedTime > PROGRESS_INTERVAL_MILLIS) {
                System.out.printf("Downloaded: %.2f MB%n", totalBytesRead / (1024.0 * 1024.0));
                lastReportedTime = currentTime;
            }
        }

        System.out.printf("Download completed! Total size: %.2f MB%n", totalBytesRead / (1024.0 * 1024.0));
        return totalBytesRead;
    }
}
