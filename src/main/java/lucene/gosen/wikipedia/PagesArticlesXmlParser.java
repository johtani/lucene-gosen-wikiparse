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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.test.util.ComponentContainer;
import lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer;

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
      System.out.println("arg[0] is old jar, arg[1] is new jar, [arg[2] is wikipedia xml file (default: ./data/jawiki-latest-pages-articles.xml)]");
      System.exit(-1);
    }
    
    String xmlPath = args.length >= 3 ? args[2] : "./data/jawiki-latest-pages-articles.xml";
    
    BufferedWriter bw = new BufferedWriter(new FileWriter("diff_result.txt"));

    System.out.println("start :: "+sdf.format(new Date(start)));
    ComponentContainer oldJarContainer = new ComponentContainer(new File[]{new File(args[0])});
    ComponentContainer newJarContainer = new ComponentContainer(new File[]{new File(args[1])});

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
      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (isStartElem(event, "page")) {
          WikipediaModel model = pageParse(reader);
          if (model != null){
            oldModelAnalyzer.analyze(model, oldJarContainer, oldResult);
            newModelAnalyzer.analyze(model, newJarContainer, newResult);
            if(compareResult(bw, model, oldResult, newResult)){
              falseCounter++;;
            }
            if(counter % 1000 == 0){
              System.out.println("success count:"+counter);
              bw.flush();
            }
            counter++;
          }
        }
      }

      reader.close();
      bw.close();
      System.out.println("falseCounter:"+falseCounter);
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
  
  private static boolean compareResult(BufferedWriter bw, WikipediaModel model, AnalyzeResult[] oldResult, AnalyzeResult[] newResult)throws IOException{

    boolean different = false;
    
    //size check
    for(int i=0;i<RESULT_SIZE;i++){
      if(oldResult[i].getTotalCost() != newResult[i].getTotalCost()){
        
        bw.append("analyze result[cost] is different!!");
        bw.newLine();
        bw.append("  old["+oldResult[i].getTotalCost()+"]");
        bw.newLine();
        bw.append("  new["+newResult[i].getTotalCost()+"]");
        bw.newLine();
        different = true;
      }
      if(different){
        if(i==0){
          bw.append(model.title);
        }else{
          //System.out.println(model.text);
        }
        //System.exit(-1);
      }
      if(!oldResult[i].getTermList().equals(newResult[i].getTermList())){
        bw.append("analyze result[termList] is different!!");
        bw.newLine();
        bw.append("  old["+oldResult[i].getTermList().toString()+"]");
        bw.newLine();
        bw.append("  new["+newResult[i].getTermList().toString()+"]");
        bw.newLine();
        different = true;
      }
      if(!oldResult[i].getPosList().equals(newResult[i].getPosList())){
        bw.append("analyze result[posList] is different!!");
        bw.newLine();
        bw.append("  old["+oldResult[i].getPosList().toString()+"]");
        bw.newLine();
        bw.append("  new["+newResult[i].getPosList().toString()+"]");
        bw.newLine();
        different = true;
      }
      break;
    }
    return different;
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
}