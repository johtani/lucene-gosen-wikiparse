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

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.test.util.ComponentContainer;
import lucene.gosen.wikipedia.WikipediaModel;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Attribute;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;


public class WikipediaModelAnalyzer {

    private final String dictionaryDir;

    public WikipediaModelAnalyzer() {
        this.dictionaryDir = null;
    }

    public WikipediaModelAnalyzer(String dictionaryDir) {
        this.dictionaryDir = dictionaryDir;
    }

    public int analyze(WikipediaModel model, ComponentContainer container, AnalyzeResult[] result) throws Exception {
        boolean titleSkipped = analyze(container, model.getTitle(), result[0]);
        boolean textSkipped = analyze(container, model.getText(), result[1]);

        // Only count as skipped if both title and text are empty
        return (titleSkipped && textSkipped) ? 1 : 0;
    }

    private boolean analyze(ComponentContainer container, String target, AnalyzeResult result) throws IllegalArgumentException, SecurityException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        result.reset();

        // Skip empty strings
        if (target == null || target.isEmpty()) {
            return true; // skipped
        }

        StringReader reader = new StringReader(target);

        try {
            // Load StreamFilter class
            Class<?> streamFilterClass = container.loadComponent("net.java.sen.filter.StreamFilter");

            // GosenTokenizer requires: StreamFilter filter, String dictionaryDir, boolean tokenizeUnknownKatakana
            Tokenizer tokenizer = (Tokenizer) container.createComponent("org.apache.lucene.analysis.gosen.GosenTokenizer",
                    new Class[]{streamFilterClass, String.class, boolean.class},
                    new Object[]{null, dictionaryDir, false});
            tokenizer.setReader(reader);
            tokenizer.reset();
            CharTermAttribute attr = tokenizer.getAttribute(CharTermAttribute.class);
            Attribute posAttr = tokenizer.getAttribute(container.loadComponent("org.apache.lucene.analysis.gosen.tokenAttributes.PartOfSpeechAttribute"));
            Attribute costAttr = tokenizer.getAttribute(container.loadComponent("org.apache.lucene.analysis.gosen.tokenAttributes.CostAttribute"));
            while (tokenizer.incrementToken()) {
                //TODO getCost execute by reflaction
                result.addCost(Integer.valueOf(costAttr.getClass().getMethod("getCost").invoke(costAttr).toString()));
                result.addTerm(attr.toString());
                result.addPos(posAttr.getClass().getMethod("getPartOfSpeech").invoke(posAttr).toString());
            }
            tokenizer.close();
        } catch (ClassNotFoundException cnfe) {
            System.err.println("ClassNotFound!!!");
            throw new RuntimeException(cnfe);
        } catch (IOException ioe) {
            System.err.println("target:[" + target + "]");
            ioe.printStackTrace();
        }
        return false; // not skipped
    }


}
