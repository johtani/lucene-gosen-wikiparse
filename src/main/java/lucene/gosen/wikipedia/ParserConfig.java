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

/**
 * パーサーの設定を保持するクラス
 */
public class ParserConfig {
    private final String oldJarPath;
    private final String newJarPath;
    private final String inputPath;
    private final int maxRecordCount;
    private final String reportFormat;

    private ParserConfig(Builder builder) {
        this.oldJarPath = builder.oldJarPath;
        this.newJarPath = builder.newJarPath;
        this.inputPath = builder.inputPath;
        this.maxRecordCount = builder.maxRecordCount;
        this.reportFormat = builder.reportFormat;
    }

    public String getOldJarPath() {
        return oldJarPath;
    }

    public String getNewJarPath() {
        return newJarPath;
    }

    public String getInputPath() {
        return inputPath;
    }

    public int getMaxRecordCount() {
        return maxRecordCount;
    }

    public String getReportFormat() {
        return reportFormat;
    }

    public boolean shouldPrintToConsole() {
        return maxRecordCount > 0 && maxRecordCount <= 10;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String oldJarPath;
        private String newJarPath;
        private String inputPath;
        private int maxRecordCount = -1; // -1 means no limit
        private String reportFormat = "both";

        public Builder oldJarPath(String oldJarPath) {
            this.oldJarPath = oldJarPath;
            return this;
        }

        public Builder newJarPath(String newJarPath) {
            this.newJarPath = newJarPath;
            return this;
        }

        public Builder inputPath(String inputPath) {
            this.inputPath = inputPath;
            return this;
        }

        public Builder maxRecordCount(int maxRecordCount) {
            this.maxRecordCount = maxRecordCount;
            return this;
        }

        public Builder reportFormat(String reportFormat) {
            this.reportFormat = reportFormat;
            return this;
        }

        public ParserConfig build() {
            if (oldJarPath == null || oldJarPath.isEmpty()) {
                throw new IllegalArgumentException("oldJarPath is required");
            }
            if (newJarPath == null || newJarPath.isEmpty()) {
                throw new IllegalArgumentException("newJarPath is required");
            }
            if (inputPath == null || inputPath.isEmpty()) {
                throw new IllegalArgumentException("inputPath is required");
            }
            if (maxRecordCount < -1 || maxRecordCount == 0) {
                throw new IllegalArgumentException("maxRecordCount must be -1 (no limit) or positive number");
            }
            if (!reportFormat.equals("text") && !reportFormat.equals("html") && !reportFormat.equals("both")) {
                throw new IllegalArgumentException("reportFormat must be 'text', 'html', or 'both'");
            }
            return new ParserConfig(this);
        }
    }
}
