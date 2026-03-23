/*
 * Copyright 2012 Jun Ohtani
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lucene.gosen.wikipedia;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.XMLEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.concurrent.Callable;

/**
 * Wikipediaのjawiki-latest-pages-articles.xmlを解析する
 */
@Command(name = "parse-xml", mixinStandardHelpOptions = true, version = "1.0",
        description = "Parse Wikipedia XML dump file and compare analysis results between two versions")
public class PagesArticlesXmlParser extends AbstractWikipediaParser implements Callable<Integer> {

    @Option(names = {"-o", "--old-jar"}, required = true,
            description = "Path to old JAR file or directory containing JAR files")
    private String oldJarPath;

    @Option(names = {"-n", "--new-jar"}, required = true,
            description = "Path to new JAR file or directory containing JAR files")
    private String newJarPath;

    @Option(names = {"-i", "--input"}, defaultValue = "./data/jawiki-latest-pages-articles.xml",
            description = "Wikipedia XML file path (default: ${DEFAULT-VALUE})")
    private String inputPath;

    @Option(names = {"-m", "--max-records"}, defaultValue = "-1",
            description = "Maximum number of records to process (default: all records)")
    private int maxRecordCount;

    @Option(names = {"-f", "--format"}, defaultValue = "both",
            description = "Report format: text, html, or both (default: ${DEFAULT-VALUE})")
    private String reportFormat;

    @Option(names = {"-w", "--workers"}, defaultValue = "1",
            description = "Number of worker threads for morphological analysis (default: ${DEFAULT-VALUE})")
    private int workerCount;

    @Option(names = {"-q", "--queue-size"}, defaultValue = "1000",
            description = "Maximum number of in-flight records for parallel processing (default: ${DEFAULT-VALUE})")
    private int queueSize;

    /**
     * XMLで使われてる日付形式
     */
    static final SimpleDateFormat sdf = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'");

    /**
     * main
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new PagesArticlesXmlParser()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // Validate report format
        if (!reportFormat.equals("text") && !reportFormat.equals("html") && !reportFormat.equals("both")) {
            System.err.println("Error: report format must be 'text', 'html', or 'both', got: " + reportFormat);
            return 1;
        }

        // Validate max record count
        if (maxRecordCount < -1 || maxRecordCount == 0) {
            System.err.println("Error: max record count must be a positive number or -1 for unlimited");
            return 1;
        }
        if (workerCount <= 0) {
            System.err.println("Error: workers must be a positive number");
            return 1;
        }
        if (queueSize <= 0) {
            System.err.println("Error: queue-size must be a positive number");
            return 1;
        }

        // ParserConfigを構築
        ParserConfig config = ParserConfig.builder()
                .oldJarPath(oldJarPath)
                .newJarPath(newJarPath)
                .inputPath(inputPath)
                .maxRecordCount(maxRecordCount)
                .reportFormat(reportFormat)
                .workerCount(workerCount)
                .queueSize(queueSize)
                .build();

        // パーサーを実行
        execute(config);
        return 0;
    }

    @Override
    protected String getDataSourceType() {
        return "XML";
    }

    @Override
    protected Object initializeDataSource(ParserConfig config) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        InputStream is = getInputStream(config.getInputPath());
        return factory.createXMLEventReader(is);
    }

    @Override
    protected WikipediaModel readNextModel(Object dataSource) throws Exception {
        XMLEventReader reader = (XMLEventReader) dataSource;
        try {
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (isStartElem(event, "page")) {
                    WikipediaModel model = pageParse(reader);
                    // スキップすべきページ（nullが返される）の場合は次のページを探す
                    if (model != null) {
                        return model;
                    }
                    // model == null の場合は continue して次の <page> を探す
                }
            }
        } catch (com.ctc.wstx.exc.WstxIOException e) {
            System.err.println("Warning: Unexpected end of XML stream. The input file may be truncated or corrupted.");
            System.err.println("Error details: " + e.getMessage());
            return null; // Signal end of processing
        } catch (javax.xml.stream.XMLStreamException e) {
            if (e.getCause() instanceof java.io.IOException) {
                System.err.println("Warning: IO error while reading XML stream. The input file may be incomplete.");
                System.err.println("Error details: " + e.getMessage());
                return null; // Signal end of processing
            }
            throw e; // Re-throw other XML stream exceptions
        }
        return null;
    }

    @Override
    protected boolean shouldProcessModel(WikipediaModel model) {
        return model != null;
    }

    @Override
    protected void closeDataSource(Object dataSource) throws Exception {
        if (dataSource != null) {
            ((XMLEventReader) dataSource).close();
        }
    }

    @Override
    protected String getTextReportFileName() {
        return "diff_result.txt";
    }

    @Override
    protected String getHtmlReportFileName() {
        return "diff_result.html";
    }

    private static InputStream getInputStream(String xmlPath) throws IOException {
        InputStream is = new java.io.BufferedInputStream(new FileInputStream(xmlPath), 65536);
        if (xmlPath.endsWith(".bz2")) {
            return new BZip2CompressorInputStream(is);
        } else if (xmlPath.endsWith(".gz")) {
            return new GzipCompressorInputStream(is);
        }
        return is;
    }

    /**
     * page element内の解析
     */
    private static WikipediaModel pageParse(XMLEventReader reader)
            throws Exception {
        WikipediaModel model = new WikipediaModel();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElem(event, "page")) break;
                // revision elementの解析は、revisonParseにて行う
            else if (isStartElem(event, "revision")) revisionParse(reader, model);
                // title
            else if (isStartElem(event, "title")) {
                String title = getText(reader, "title", false);
                // タイトルにコロンが含まれる場合は管理用記事なのでスキップする
                if (title.indexOf(':') != -1) return null;
                // (曖昧さ回避)や(音楽)などの注釈文字を外す
                int posStart = title.indexOf(" (");
                int posEnd = title.indexOf(')', posStart);
                if (posStart != -1 && posEnd != -1) {
                    model.setTitle(title.substring(0, posStart));
                    model.setTitleAnnotation(title.substring(posStart + 2, posEnd));
                } else {
                    model.setTitle(title);
                }
            } else if (isStartElem(event, "id")) model.setId(getText(reader, "id", false));
        }
        return model;
    }

    /**
     * revision element内の解析
     */
    private static void revisionParse(XMLEventReader reader, WikipediaModel model)
            throws Exception {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElem(event, "revision")) break;
            else if (isStartElem(event, "text")) model
                    .setText(getText(reader, "text", true));
            else if (isStartElem(event, "timestamp")) model.setLastModified(sdf
                    .parse(getText(reader, "timestamp", false)));
        }
    }

    /**
     * 指定のend tagを発見するまで、テキストを取得
     */
    private static String getText(XMLEventReader reader, String name, boolean normalizeForAnalysis)
            throws Exception {
        StringBuilder builder = new StringBuilder();
        try {
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (isEndElem(event, name)) break;
                else if (event.isCharacters()) {
                    builder.append(event.asCharacters().getData());
                }
            }
        } catch (com.ctc.wstx.exc.WstxIOException e) {
            // Stream ended unexpectedly while reading element content
            System.err.println("Warning: Stream ended while reading <" + name + "> element");
            // Return whatever was collected so far
        }
        String rawText = builder.toString();
        if (!normalizeForAnalysis) {
            return rawText.trim();
        }
        return normalizeTextForAnalysis(rawText);
    }

    private static String normalizeTextForAnalysis(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String normalizedLineBreaks = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        StringBuilder builder = new StringBuilder(normalizedLineBreaks.length());

        int i = 0;
        while (i < normalizedLineBreaks.length()) {
            char ch = normalizedLineBreaks.charAt(i);
            if (ch != '\n') {
                builder.append(ch);
                i++;
                continue;
            }

            while (i < normalizedLineBreaks.length() && normalizedLineBreaks.charAt(i) == '\n') {
                i++;
            }

            int nextIndex = i;
            while (nextIndex < normalizedLineBreaks.length()) {
                char nextChar = normalizedLineBreaks.charAt(nextIndex);
                if (nextChar == ' ' || nextChar == '\t' || nextChar == '\f') {
                    nextIndex++;
                    continue;
                }
                break;
            }

            if (endsWithSentenceBoundary(builder)) {
                trimHorizontalWhitespace(builder);
                i = nextIndex;
                continue;
            }

            builder.append('\n');
            i = nextIndex;
        }

        return builder.toString().strip();
    }

    private static boolean endsWithSentenceBoundary(StringBuilder builder) {
        for (int i = builder.length() - 1; i >= 0; i--) {
            char ch = builder.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            return ch == '。' || ch == '．' || ch == '.' || ch == '!' || ch == '?' || ch == '！' || ch == '？';
        }
        return false;
    }

    private static void trimHorizontalWhitespace(StringBuilder builder) {
        while (!builder.isEmpty()) {
            char ch = builder.charAt(builder.length() - 1);
            if (ch == ' ' || ch == '\t' || ch == '\f') {
                builder.setLength(builder.length() - 1);
                continue;
            }
            break;
        }
    }

    /**
     * 指定名のStart Elementか判定する
     */
    private static boolean isStartElem(XMLEvent event, String name) {
        return event.getEventType() == XMLStreamConstants.START_ELEMENT
                && name.equals(event.asStartElement().getName().getLocalPart());
    }

    /**
     * 指定名のEnd Elementか判定する
     */
    private static boolean isEndElem(XMLEvent event, String name) {
        return event.getEventType() == XMLStreamConstants.END_ELEMENT
                && name.equals(event.asEndElement().getName().getLocalPart());
    }

}
