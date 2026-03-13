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
package lucene.gosen.wikipedia.analyzer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.test.util.ComponentContainer;
import lucene.gosen.wikipedia.WikipediaModel;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Attribute;


public class WikipediaModelAnalyzer {
  
  public void analyze(WikipediaModel model, ComponentContainer container, AnalyzeResult[] result)throws Exception{
    analyze(container, model.getTitle(), result[0]);
    analyze(container, model.getText(), result[1]);
  }
  
  private void analyze(ComponentContainer container, String target, AnalyzeResult result) throws NumberFormatException, IllegalArgumentException, SecurityException, IllegalAccessException, InvocationTargetException, NoSuchMethodException{
    result.reset();
    
    StringReader reader = new StringReader(target);

    try{
      Tokenizer tokenizer = (Tokenizer)container.createComponent("org.apache.lucene.analysis.gosen.GosenTokenizer", new Class[]{Reader.class} ,new Object[]{reader});
      CharTermAttribute attr = (CharTermAttribute)tokenizer.getAttribute(CharTermAttribute.class);
      Attribute posAttr = (Attribute)tokenizer.getAttribute(container.loadComponent("org.apache.lucene.analysis.gosen.tokenAttributes.PartOfSpeechAttribute"));
      Attribute costAttr = (Attribute)tokenizer.getAttribute(container.loadComponent("org.apache.lucene.analysis.gosen.tokenAttributes.CostAttribute"));
      while (tokenizer.incrementToken()) {
        //TODO getCost execute by reflaction  
        result.addCost( Integer.valueOf(costAttr.getClass().getMethod("getCost").invoke(costAttr).toString())  );
        result.addTerm(attr.toString());
        result.addPos(posAttr.toString());
      }
    }catch(ClassNotFoundException cnfe){
      System.err.println("ClassNotFound!!!");
      throw new RuntimeException(cnfe);
    }catch(IOException ioe){
      System.err.println("target:["+target+"]");
      ioe.printStackTrace();
    }
  }
 
  
}
