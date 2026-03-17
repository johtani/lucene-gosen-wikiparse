package lucene.gosen.wikipedia;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
 * Maven CentralからluceneとGosenのJARファイルをダウンロードするクラス
 */
@Command(name = "download-jars", mixinStandardHelpOptions = true, version = "1.0",
        description = "Download lucene-gosen and lucene-core JAR files from Maven Central")
public class MavenJarDownloader implements Callable<Integer> {

    private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";
    private static final String LUCENE_GOSEN_GROUP_ID = "com.github.lucene-gosen";
    private static final String LUCENE_GOSEN_ARTIFACT_ID = "lucene-gosen";
    private static final String LUCENE_CORE_GROUP_ID = "org.apache.lucene";
    private static final String LUCENE_CORE_ARTIFACT_ID = "lucene-core";

    @Option(names = {"-v", "--version"}, required = true,
            description = "Version of lucene-gosen to download")
    private String version;

    @Option(names = {"-d", "--destination"},
            description = "Destination directory (default: ./lib/<version>)")
    private String destination;

    @Option(names = {"-c", "--classifier"}, defaultValue = "ipadic",
            description = "Classifier (default: ${DEFAULT-VALUE})")
    private String classifier;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MavenJarDownloader()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        String destDir = destination != null ? destination : "./lib/" + version;

        try {
            downloadLuceneGosenWithDependencies(version, destDir, classifier);
            System.out.println("All downloads completed successfully!");
            return 0;
        } catch (Exception e) {
            System.err.println("Error downloading files: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * lucene-gosenとその依存関係（lucene-core）をダウンロードする
     */
    public static void downloadLuceneGosenWithDependencies(String version, String destDir, String classifier) throws Exception {
        Path destPath = Paths.get(destDir);
        if (Files.notExists(destPath)) {
            Files.createDirectories(destPath);
            System.out.println("Created directory: " + destPath.toAbsolutePath());
        }

        // 1. lucene-gosenをダウンロード
        System.out.println("\n=== Downloading lucene-gosen ===");
        String gosenJarUrl = buildJarUrl(LUCENE_GOSEN_GROUP_ID, LUCENE_GOSEN_ARTIFACT_ID, version, classifier);
        String gosenJarPath = destDir + "/" + LUCENE_GOSEN_ARTIFACT_ID + "-" + version +
                (classifier != null ? "-" + classifier : "") + ".jar";
        downloadFile(gosenJarUrl, gosenJarPath);

        // 2. POMファイルから依存関係を取得
        System.out.println("\n=== Fetching dependencies from POM ===");
        String pomUrl = buildPomUrl(version);
        String luceneCoreVersion = extractLuceneCoreVersion(pomUrl, version);

        // 3. lucene-coreをダウンロード
        System.out.println("\n=== Downloading lucene-core ===");
        String coreJarUrl = buildJarUrl(LUCENE_CORE_GROUP_ID, LUCENE_CORE_ARTIFACT_ID, luceneCoreVersion, null);
        String coreJarPath = destDir + "/" + LUCENE_CORE_ARTIFACT_ID + "-" + luceneCoreVersion + ".jar";
        downloadFile(coreJarUrl, coreJarPath);
    }

    /**
     * Maven CentralのJAR URLを構築
     */
    private static String buildJarUrl(String groupId, String artifactId, String version, String classifier) {
        String groupPath = groupId.replace('.', '/');
        String jarName = artifactId + "-" + version + (classifier != null ? "-" + classifier : "") + ".jar";
        return String.format("%s/%s/%s/%s/%s",
                MAVEN_CENTRAL_BASE, groupPath, artifactId, version, jarName);
    }

    /**
     * Maven CentralのPOM URLを構築
     */
    private static String buildPomUrl(String version) {
        String groupPath = MavenJarDownloader.LUCENE_GOSEN_GROUP_ID.replace('.', '/');
        return String.format("%s/%s/%s/%s/%s-%s.pom",
                MAVEN_CENTRAL_BASE, groupPath, MavenJarDownloader.LUCENE_GOSEN_ARTIFACT_ID, version, MavenJarDownloader.LUCENE_GOSEN_ARTIFACT_ID, version);
    }

    /**
     * POMファイルからlucene-coreのバージョンを抽出
     */
    private static String extractLuceneCoreVersion(String pomUrl, String defaultVersion) {
        System.out.println("Fetching POM from: " + pomUrl);

        try {
            URL url = URI.create(pomUrl).toURL();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());

            // dependenciesセクションからlucene-coreを探す
            NodeList dependencies = doc.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependency = (Element) dependencies.item(i);
                String artifactId = getElementText(dependency, "artifactId");
                if (LUCENE_CORE_ARTIFACT_ID.equals(artifactId)) {
                    String version = getElementText(dependency, "version");
                    if (version != null && !version.isEmpty()) {
                        System.out.println("Found lucene-core dependency version: " + version);
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Could not parse POM file, using default version: " + defaultVersion);
        }

        // 見つからない場合は、lucene-gosenのバージョンと同じと仮定
        System.out.println("Using default lucene-core version: " + defaultVersion);
        return defaultVersion;
    }

    /**
     * XML要素からテキストを取得
     */
    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
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
