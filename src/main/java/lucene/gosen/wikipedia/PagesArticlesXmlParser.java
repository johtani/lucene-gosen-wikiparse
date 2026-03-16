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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.test.util.ComponentContainer;
import lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer;
import lucene.gosen.wikipedia.report.ExecutionInfo;
import lucene.gosen.wikipedia.report.HtmlReportGenerator;
import lucene.gosen.wikipedia.report.ReportGenerator;
import lucene.gosen.wikipedia.report.TextReportGenerator;

/**
 * Wikipediaのjawiki-latest-pages-articles.xmlを解析する
 */
public class PagesArticlesXmlParser {

  /** XMLで使われてる日付形式 */
  static final SimpleDateFormat sdf = new SimpleDateFormat(
      "yyyy-MM-dd'T'HH:mm:ss'Z'");

  public static final int RESULT_SIZE = 2;
  /** main */
  public static void main(String[] args) throws Exception {

    long start = System.currentTimeMillis();
    if (args.length < 2) {
      System.out.println("arg[0] is old jar or directory, arg[1] is new jar or directory, [arg[2] is wikipedia xml file (default: ./data/jawiki-latest-pages-articles.xml)], [arg[3] is max record count (optional, default: all records)], [arg[4] is report format (text|html|both, default: both)]");
      System.exit(-1);
    }

    String xmlPath = args.length >= 3 ? args[2] : "./data/jawiki-latest-pages-articles.xml";

    // Parse max record count with validation
    int maxRecordCount = -1; // -1 means no limit
    if (args.length >= 4) {
      try {
        maxRecordCount = Integer.parseInt(args[3]);
        if (maxRecordCount <= 0) {
          System.err.println("Error: max record count must be a positive number");
          System.exit(-1);
        }
      } catch (NumberFormatException e) {
        System.err.println("Error: arg[3] must be a valid number, got: " + args[3]);
        System.exit(-1);
      }
    }

    // Parse report format
    String reportFormat = args.length >= 5 ? args[4].toLowerCase() : "both";
    if (!reportFormat.equals("text") && !reportFormat.equals("html") && !reportFormat.equals("both")) {
      System.err.println("Error: report format must be 'text', 'html', or 'both', got: " + reportFormat);
      System.exit(-1);
    }

    // 実行情報を収集
    ExecutionInfo execInfo = new ExecutionInfo();
    execInfo.setOldJarPath(args[0]);
    execInfo.setNewJarPath(args[1]);
    execInfo.setXmlPath(xmlPath);
    execInfo.setMaxRecordCount(maxRecordCount);
    execInfo.setReportFormat(reportFormat);
    execInfo.setStartTime(new Date(start));

    // JAR ファイル情報を収集
    File[] oldJarFiles = getJarFiles(args[0]);
    File[] newJarFiles = getJarFiles(args[1]);
    execInfo.setOldJarFiles(Arrays.stream(oldJarFiles).map(File::getName).toList());
    execInfo.setNewJarFiles(Arrays.stream(newJarFiles).map(File::getName).toList());

    // レポートジェネレーターを初期化
    List<ReportGenerator> reportGenerators = new ArrayList<>();
    if (reportFormat.equals("text") || reportFormat.equals("both")) {
      TextReportGenerator textGen = new TextReportGenerator("diff_result.txt");
      textGen.setExecutionInfo(execInfo);
      reportGenerators.add(textGen);
    }
    if (reportFormat.equals("html") || reportFormat.equals("both")) {
      HtmlReportGenerator htmlGen = new HtmlReportGenerator();
      htmlGen.setExecutionInfo(execInfo);
      reportGenerators.add(htmlGen);
    }

    System.out.println("start :: "+sdf.format(new Date(start)));
    ComponentContainer oldJarContainer = new ComponentContainer(oldJarFiles);
    ComponentContainer newJarContainer = new ComponentContainer(newJarFiles);

    WikipediaModelAnalyzer oldModelAnalyzer = (WikipediaModelAnalyzer)oldJarContainer.createComponent("lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer", null, null);
    WikipediaModelAnalyzer newModelAnalyzer = (WikipediaModelAnalyzer)newJarContainer.createComponent("lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer", null, null);


    AnalyzeResult[] oldResult = new AnalyzeResult[RESULT_SIZE];
    AnalyzeResult[] newResult = new AnalyzeResult[RESULT_SIZE];
    for(int i=0;i<RESULT_SIZE;i++){
      oldResult[i] = new AnalyzeResult();
      newResult[i] = new AnalyzeResult();
    }

    XMLInputFactory factory = XMLInputFactory.newInstance();
    try (InputStream is = getInputStream(xmlPath)) {
      XMLEventReader reader = factory.createXMLEventReader(is);
      int counter = 0;
      int falseCounter = 0;
      int skippedCounter = 0;
      boolean printToConsole = (maxRecordCount > 0 && maxRecordCount <= 10);

      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (isStartElem(event, "page")) {
          WikipediaModel model = pageParse(reader);
          if (model != null){
            int skipped = oldModelAnalyzer.analyze(model, oldJarContainer, oldResult);
            newModelAnalyzer.analyze(model, newJarContainer, newResult);
            skippedCounter += skipped;
            boolean hasDifference = compareResult(model, oldResult, newResult);
            if(hasDifference){
              falseCounter++;
            }

            // 各レポートジェネレーターに結果を追加
            for (ReportGenerator generator : reportGenerators) {
              generator.addDiffResult(model, oldResult, newResult, hasDifference, printToConsole);
            }

            if (printToConsole) {
              printResults(counter, model, oldResult, newResult);
            }

            if(counter % 1000 == 0){
              System.out.println("success count:"+counter);
              for (ReportGenerator generator : reportGenerators) {
                generator.flush();
              }
            }
            counter++;

            // Check if max record count is reached
            if (maxRecordCount > 0 && counter >= maxRecordCount) {
              System.out.println("Reached max record count: " + maxRecordCount);
              break;
            }
          }
        }
      }

      reader.close();

      // 実行情報の最終更新
      execInfo.setEndTime(new Date());
      execInfo.setDurationMs(System.currentTimeMillis() - start);
      execInfo.setTotalProcessed(counter);
      execInfo.setDifferenceCount(falseCounter);
      execInfo.setSkippedCount(skippedCounter);

      // レポート生成
      for (ReportGenerator generator : reportGenerators) {
        if (generator instanceof TextReportGenerator) {
          generator.generateReport("diff_result.txt");
          System.out.println("Text report generated: diff_result.txt");
        } else if (generator instanceof HtmlReportGenerator) {
          generator.generateReport("diff_result.html");
          System.out.println("HTML report generated: diff_result.html");
        }
        generator.close();
      }

      System.out.println("total processed: " + counter);
      System.out.println("falseCounter: " + falseCounter);
      System.out.println("skippedCounter: " + skippedCounter);
      System.out.println((System.currentTimeMillis() - start) + "msec");
    }
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
  
  private static boolean compareResult(WikipediaModel model, AnalyzeResult[] oldResult, AnalyzeResult[] newResult) {
    boolean different = false;

    //size check
    for(int i=0;i<RESULT_SIZE;i++){
      if(oldResult[i].getTotalCost() != newResult[i].getTotalCost()){
        different = true;
      }
      if(!oldResult[i].getTermList().equals(newResult[i].getTermList())){
        different = true;
      }
      if(!oldResult[i].getPosList().equals(newResult[i].getPosList())){
        different = true;
      }
      break;
    }
    return different;
  }

  private static void printResults(int counter, WikipediaModel model,
                                    AnalyzeResult[] oldResult, AnalyzeResult[] newResult) {
    System.out.println("\n=== Record #" + (counter + 1) + " ===");
    System.out.println("Title: \"" + model.getTitle() + "\"");
    System.out.println("Text: \"" + model.getText() + "\"");
    System.out.println("\n[OLD Results]");
    System.out.println("  Terms: " + oldResult[0].getTermList());
    System.out.println("  POS: " + oldResult[0].getPosList());
    System.out.println("  Total Cost: " + oldResult[0].getTotalCost());
    System.out.println("\n[NEW Results]");
    System.out.println("  Terms: " + newResult[0].getTermList());
    System.out.println("  POS: " + newResult[0].getPosList());
    System.out.println("  Total Cost: " + newResult[0].getTotalCost());
  }

  /** page element内の解析 */
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

  /** revision element内の解析 */
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

  /** 指定のend tagを発見するまで、CHARACTERSを取得 */
  private static String getText(XMLEventReader reader, String name)
      throws Exception {
    StringBuilder builder = new StringBuilder();
    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();
      if (isEndElem(event, name)) break;
      else if (event.getEventType() == XMLStreamConstants.CHARACTERS) {
        String data = event.asCharacters().getData().trim();
        if (data.length() > 0) builder.append(data);
      }
    }
    return builder.toString();
  }

  /** 指定名のStart Elementか判定する */
  private static boolean isStartElem(XMLEvent event, String name) {
    return event.getEventType() == XMLStreamConstants.START_ELEMENT
        && name.equals(event.asStartElement().getName().getLocalPart());
  }

  /** 指定名のEnd Elementか判定する */
  private static boolean isEndElem(XMLEvent event, String name) {
    return event.getEventType() == XMLStreamConstants.END_ELEMENT
        && name.equals(event.asEndElement().getName().getLocalPart());
  }

  /**
   * 指定されたパスからJARファイルの配列を取得する
   * ディレクトリが指定された場合は、そのディレクトリ内の全てのJARファイルを返す
   * ファイルが指定された場合は、そのファイルのみを含む配列を返す
   */
  private static File[] getJarFiles(String path) {
    File file = new File(path);

    if (file.isDirectory()) {
      // ディレクトリの場合、.jarファイルのみをフィルタリング
      File[] jarFiles = file.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
      if (jarFiles == null || jarFiles.length == 0) {
        throw new RuntimeException("No JAR files found in directory: " + path);
      }
      System.out.println("Found " + jarFiles.length + " JAR file(s) in " + path);
      for (File jarFile : jarFiles) {
        System.out.println("  - " + jarFile.getName());
      }
      return jarFiles;
    } else if (file.isFile()) {
      // ファイルの場合、単一のファイルを含む配列を返す
      System.out.println("Using JAR file: " + file.getName());
      return new File[]{file};
    } else {
      throw new RuntimeException("Path is neither a file nor a directory: " + path);
    }
  }
}