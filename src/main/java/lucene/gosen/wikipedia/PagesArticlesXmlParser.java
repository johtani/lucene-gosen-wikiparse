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

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.XMLEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;

/**
 * Wikipediaのjawiki-latest-pages-articles.xmlを解析する
 */
public class PagesArticlesXmlParser extends AbstractWikipediaParser {

    /**
     * XMLで使われてる日付形式
     */
    static final SimpleDateFormat sdf = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'");

    /**
     * main
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("arg[0] is old jar or directory, arg[1] is new jar or directory, [arg[2] is wikipedia xml file (default: ./data/jawiki-latest-pages-articles.xml)], [arg[3] is max record count (optional, default: all records)], [arg[4] is report format (text|html|both, default: both)]");
            System.exit(-1);
        }

        String xmlPath = args.length >= 3 ? args[2] : "./data/jawiki-latest-pages-articles.xml";

        // Parse max record count with validation
        int maxRecordCount = ParserUtils.parseMaxRecordCount(args, 3, -1);

        // Parse report format
        String reportFormat = ParserUtils.parseReportFormat(args, 4, "both");

        // ParserConfigを構築
        ParserConfig config = ParserConfig.builder()
                .oldJarPath(args[0])
                .newJarPath(args[1])
                .inputPath(xmlPath)
                .maxRecordCount(maxRecordCount)
                .reportFormat(reportFormat)
                .build();

        // パーサーを実行
        PagesArticlesXmlParser parser = new PagesArticlesXmlParser();
        parser.execute(config);
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
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isStartElem(event, "page")) {
                return pageParse(reader);
            }
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
                String title = getText(reader, "title");
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
            } else if (isStartElem(event, "id")) model.setId(getText(reader, "id"));
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
                    .setText(getText(reader, "text"));
            else if (isStartElem(event, "timestamp")) model.setLastModified(sdf
                    .parse(getText(reader, "timestamp")));
        }
    }

    /**
     * 指定のend tagを発見するまで、CHARACTERSを取得
     */
    private static String getText(XMLEventReader reader, String name)
            throws Exception {
        StringBuilder builder = new StringBuilder();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElem(event, name)) break;
            else if (event.getEventType() == XMLStreamConstants.CHARACTERS) {
                String data = event.asCharacters().getData().trim();
                if (!data.isEmpty()) builder.append(data);
            }
        }
        return builder.toString();
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