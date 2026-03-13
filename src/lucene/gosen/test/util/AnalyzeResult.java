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
package lucene.gosen.test.util;

import java.util.ArrayList;
import java.util.List;

public class AnalyzeResult {
  
  private int totalCost = 0;
  private List<String> termList = new ArrayList<String>();
  private List<String> posList = new ArrayList<String>();
  
  public void addPos(String pos){
    this.posList.add(pos);
  }
  
  public void addTerm(String term){
    this.termList.add(term);
  }
  
  public void addCost(int cost){
    this.totalCost+=cost;
  }
  
  public int getTotalCost() {
    return totalCost;
  }

  public List<String> getTermList() {
    return termList;
  }

  public List<String> getPosList() {
    return posList;
  }

  @Override
  public String toString() {
    return "AnalyzeResult [totalCost=" + totalCost + ", termList=" + termList
        + ", posList=" + posList + "]";
  }
  
  public void reset(){
    this.termList.clear();
    this.posList.clear();
    this.totalCost = 0;
  }

}
