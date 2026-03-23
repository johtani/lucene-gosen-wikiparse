package lucene.gosen.wikipedia;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.lang.reflect.Method;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PagesArticlesXmlParserTest {

    @TempDir
    Path tempDir;

    private static final String DUMMY_XML =
            """
                    <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.10/">
                      <page>
                        <title>Test Page</title>
                        <revision>
                          <id>1</id>
                          <timestamp>2023-01-01T00:00:00Z</timestamp>
                          <text>This is a test.</text>
                        </revision>
                      </page>
                    </mediawiki>""";

    @Test
    public void testGetInputStreamPlain() throws IOException {
        File file = tempDir.resolve("test.xml").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(DUMMY_XML.getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream is = invokeGetInputStream(file.getAbsolutePath());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            assertEquals(DUMMY_XML.split("\n")[0], reader.readLine());
        }
    }

    @Test
    public void testGetInputStreamGzip() throws IOException {
        File file = tempDir.resolve("test.xml.gz").toFile();
        try (GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(new FileOutputStream(file))) {
            gzos.write(DUMMY_XML.getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream is = invokeGetInputStream(file.getAbsolutePath());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            assertEquals(DUMMY_XML.split("\n")[0], reader.readLine());
        }
    }

    @Test
    public void testGetInputStreamBzip2() throws IOException {
        File file = tempDir.resolve("test.xml.bz2").toFile();
        try (BZip2CompressorOutputStream bzos = new BZip2CompressorOutputStream(new FileOutputStream(file))) {
            bzos.write(DUMMY_XML.getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream is = invokeGetInputStream(file.getAbsolutePath());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            assertEquals(DUMMY_XML.split("\n")[0], reader.readLine());
        }
    }

    @Test
    public void testTextNewLineRemovedAfterSentenceBoundary() throws Exception {
        String xml = """
                <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.10/">
                  <page>
                    <title>Test Page</title>
                    <revision>
                      <id>1</id>
                      <timestamp>2023-01-01T00:00:00Z</timestamp>
                      <text>文1。
                文2</text>
                    </revision>
                  </page>
                </mediawiki>
                """;

        WikipediaModel model = parseFirstPage(xml);
        assertNotNull(model);
        assertEquals("文1。文2", model.getText());
    }

    @Test
    public void testTextNewLinePreservedWithoutSentenceBoundary() throws Exception {
        String xml = """
                <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.10/">
                  <page>
                    <title>Test Page</title>
                    <revision>
                      <id>1</id>
                      <timestamp>2023-01-01T00:00:00Z</timestamp>
                      <text>文1
                文2</text>
                    </revision>
                  </page>
                </mediawiki>
                """;

        WikipediaModel model = parseFirstPage(xml);
        assertNotNull(model);
        assertEquals("文1\n文2", model.getText());
    }

    @Test
    public void testTextLeadingTrailingWhitespaceRemoved() throws Exception {
        String xml = """
                <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.10/">
                  <page>
                    <title>Test Page</title>
                    <revision>
                      <id>1</id>
                      <timestamp>2023-01-01T00:00:00Z</timestamp>
                      <text>

                文1。
                文2

                      </text>
                    </revision>
                  </page>
                </mediawiki>
                """;

        WikipediaModel model = parseFirstPage(xml);
        assertNotNull(model);
        assertEquals("文1。文2", model.getText());
    }

    @Test
    public void testTextWithCDataAndSplitEventsKeepsMeaningfulNewline() throws Exception {
        String xml = """
                <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.10/">
                  <page>
                    <title>Test Page</title>
                    <revision>
                      <id>1</id>
                      <timestamp>2023-01-01T00:00:00Z</timestamp>
                      <text>文1<![CDATA[
                文2]]>文3</text>
                    </revision>
                  </page>
                </mediawiki>
                """;

        WikipediaModel model = parseFirstPage(xml);
        assertNotNull(model);
        assertEquals("文1\n文2文3", model.getText());
    }

    @Test
    public void testTitleStillTrimmed() throws Exception {
        String xml = """
                <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.10/">
                  <page>
                    <title>  Test Page  </title>
                    <revision>
                      <id>1</id>
                      <timestamp>2023-01-01T00:00:00Z</timestamp>
                      <text>test</text>
                    </revision>
                  </page>
                </mediawiki>
                """;

        WikipediaModel model = parseFirstPage(xml);
        assertNotNull(model);
        assertEquals("Test Page", model.getTitle());
    }

    private InputStream invokeGetInputStream(String path) throws IOException {
        try {
            Method method = PagesArticlesXmlParser.class.getDeclaredMethod("getInputStream", String.class);
            method.setAccessible(true);
            return (InputStream) method.invoke(null, path);
        } catch (Exception e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    private WikipediaModel parseFirstPage(String xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        Method method = PagesArticlesXmlParser.class.getDeclaredMethod("pageParse", XMLEventReader.class);
        method.setAccessible(true);

        XMLEventReader reader = factory.createXMLEventReader(new StringReader(xml));
        try {
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (event.isStartElement()
                        && "page".equals(event.asStartElement().getName().getLocalPart())) {
                    return (WikipediaModel) method.invoke(null, reader);
                }
            }
        } finally {
            reader.close();
        }
        return null;
    }
}
