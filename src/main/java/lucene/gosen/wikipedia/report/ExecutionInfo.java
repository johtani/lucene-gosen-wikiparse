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
package lucene.gosen.wikipedia.report;

import java.util.Date;
import java.util.List;

/**
 * レポート生成のための実行情報を保持するクラス
 */
public class ExecutionInfo {
    private String oldJarPath;
    private String newJarPath;
    private String dataSourcePath;  // XMLまたはParquetファイルのパス
    private String dataSourceType;  // "XML" または "Parquet"
    private int maxRecordCount;
    private Date startTime;
    private Date endTime;
    private long durationMs;
    private List<String> oldJarFiles;
    private List<String> newJarFiles;

    // 処理結果のサマリー
    private int totalProcessed;
    private int differenceCount;
    private int skippedCount;
    private int failedCount;

    public String getOldJarPath() {
        return oldJarPath;
    }

    public void setOldJarPath(String oldJarPath) {
        this.oldJarPath = oldJarPath;
    }

    public String getNewJarPath() {
        return newJarPath;
    }

    public void setNewJarPath(String newJarPath) {
        this.newJarPath = newJarPath;
    }

    public String getDataSourcePath() {
        return dataSourcePath;
    }

    public void setDataSourcePath(String dataSourcePath) {
        this.dataSourcePath = dataSourcePath;
    }

    public String getDataSourceType() {
        return dataSourceType;
    }

    public void setDataSourceType(String dataSourceType) {
        this.dataSourceType = dataSourceType;
    }

    // 後方互換性のために残す
    @Deprecated
    public String getXmlPath() {
        return dataSourcePath;
    }

    @Deprecated
    public void setXmlPath(String xmlPath) {
        this.dataSourcePath = xmlPath;
        this.dataSourceType = "XML";
    }

    public int getMaxRecordCount() {
        return maxRecordCount;
    }

    public void setMaxRecordCount(int maxRecordCount) {
        this.maxRecordCount = maxRecordCount;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public List<String> getOldJarFiles() {
        return oldJarFiles;
    }

    public void setOldJarFiles(List<String> oldJarFiles) {
        this.oldJarFiles = oldJarFiles;
    }

    public List<String> getNewJarFiles() {
        return newJarFiles;
    }

    public void setNewJarFiles(List<String> newJarFiles) {
        this.newJarFiles = newJarFiles;
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(int totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public int getDifferenceCount() {
        return differenceCount;
    }

    public void setDifferenceCount(int differenceCount) {
        this.differenceCount = differenceCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }
}
