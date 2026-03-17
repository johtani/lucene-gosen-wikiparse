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

import lucene.gosen.test.util.ComponentContainer;
import lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer;

import java.io.File;

/**
 * アナライザーコンテナを管理するクラス
 */
public class AnalyzerContainerManager {
    private final ComponentContainer oldJarContainer;
    private final ComponentContainer newJarContainer;
    private final WikipediaModelAnalyzer oldModelAnalyzer;
    private final WikipediaModelAnalyzer newModelAnalyzer;

    public AnalyzerContainerManager(File[] oldJarFiles, File[] newJarFiles) throws ClassNotFoundException {
        this.oldJarContainer = new ComponentContainer(oldJarFiles);
        this.newJarContainer = new ComponentContainer(newJarFiles);

        this.oldModelAnalyzer = (WikipediaModelAnalyzer) oldJarContainer.createComponent(
                "lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer", null, null);
        this.newModelAnalyzer = (WikipediaModelAnalyzer) newJarContainer.createComponent(
                "lucene.gosen.wikipedia.analyzer.WikipediaModelAnalyzer", null, null);
    }

    public ComponentContainer getOldJarContainer() {
        return oldJarContainer;
    }

    public ComponentContainer getNewJarContainer() {
        return newJarContainer;
    }

    public WikipediaModelAnalyzer getOldModelAnalyzer() {
        return oldModelAnalyzer;
    }

    public WikipediaModelAnalyzer getNewModelAnalyzer() {
        return newModelAnalyzer;
    }
}
