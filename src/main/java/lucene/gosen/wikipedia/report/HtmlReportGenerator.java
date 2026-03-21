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

import lucene.gosen.test.util.AnalyzeResult;
import lucene.gosen.wikipedia.WikipediaModel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * HTML形式のレポート生成クラス
 */
public class HtmlReportGenerator implements ReportGenerator {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int RESULT_SIZE = 2;

    private ExecutionInfo execInfo;
    private Path diffTempFile;
    private BufferedWriter diffWriter;
    private int diffCount;

    @Override
    public void setExecutionInfo(ExecutionInfo info) {
        this.execInfo = info;
    }

    @Override
    public void addDiffResult(WikipediaModel model, AnalyzeResult[] oldResult,
                              AnalyzeResult[] newResult, boolean hasDifference,
                              boolean printToConsole) throws IOException {
        if (!hasDifference) {
            return; // 差分がない場合は記録しない
        }

        ensureDiffWriter();
        diffCount++;
        writeDiffRecord(diffWriter, diffCount, model, oldResult, newResult);
    }

    private List<String> detectDiffTypes(AnalyzeResult[] oldResult, AnalyzeResult[] newResult) {
        List<String> types = new ArrayList<>();
        for (int i = 0; i < RESULT_SIZE; i++) {
            if (oldResult[i].getTotalCost() != newResult[i].getTotalCost()) {
                types.add("cost");
            }
            if (!oldResult[i].getTermList().equals(newResult[i].getTermList())) {
                if (!types.contains("term")) types.add("term");
            }
            if (!oldResult[i].getPosList().equals(newResult[i].getPosList())) {
                if (!types.contains("pos")) types.add("pos");
            }
        }
        return types;
    }

    @Override
    public void flush() throws IOException {
        if (diffWriter != null) {
            diffWriter.flush();
        }
    }

    @Override
    public void generateReport(String outputPath) throws IOException {
        try (BufferedWriter writer = newUtf8Writer(
                Path.of(outputPath),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            writeHtmlHeader(writer);
            writeExecutionInfo(writer);
            writeSummary(writer);
            writeDiffDetails(writer);
            writeHtmlFooter(writer);
        }
    }

    private void writeHtmlHeader(BufferedWriter writer) throws IOException {
        writer.write("<!DOCTYPE html>\n");
        writer.write("<html lang=\"ja\">\n");
        writer.write("<head>\n");
        writer.write("  <meta charset=\"UTF-8\">\n");
        writer.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        writer.write("  <title>Wikipedia解析 差分レポート</title>\n");
        writer.write("  <style>\n");
        writeStyles(writer);
        writer.write("  </style>\n");
        writer.write("</head>\n");
        writer.write("<body>\n");
        writer.write("  <div class=\"container\">\n");
        writer.write("    <h1>Wikipedia解析 差分レポート</h1>\n");
    }

    private void writeStyles(BufferedWriter writer) throws IOException {
        writer.write("    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }\n");
        writer.write("    .container { max-width: 1400px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        writer.write("    h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }\n");
        writer.write("    h2 { color: #555; margin-top: 30px; border-bottom: 2px solid #ddd; padding-bottom: 8px; }\n");
        writer.write("    .info-section { background: #f9f9f9; padding: 20px; margin: 20px 0; border-radius: 5px; border-left: 4px solid #4CAF50; }\n");
        writer.write("    .info-table { width: 100%; border-collapse: collapse; margin-top: 10px; }\n");
        writer.write("    .info-table th { text-align: left; padding: 8px; background: #e8e8e8; width: 200px; }\n");
        writer.write("    .info-table td { padding: 8px; border-bottom: 1px solid #ddd; }\n");
        writer.write("    .jar-list { margin: 10px 0; padding-left: 20px; }\n");
        writer.write("    .jar-list li { padding: 3px 0; color: #666; }\n");
        writer.write("    .summary-section { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin: 20px 0; }\n");
        writer.write("    .summary-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; text-align: center; }\n");
        writer.write("    .summary-card.success { background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%); }\n");
        writer.write("    .summary-card.warning { background: linear-gradient(135deg, #ff9800 0%, #f57c00 100%); }\n");
        writer.write("    .summary-card.info { background: linear-gradient(135deg, #2196F3 0%, #1976D2 100%); }\n");
        writer.write("    .summary-card .number { font-size: 36px; font-weight: bold; margin: 10px 0; }\n");
        writer.write("    .summary-card .label { font-size: 14px; opacity: 0.9; }\n");
        writer.write("    .diff-table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
        writer.write("    .diff-table th { background: #4CAF50; color: white; padding: 12px; text-align: left; position: sticky; top: 0; }\n");
        writer.write("    .diff-table td { padding: 10px; border-bottom: 1px solid #ddd; }\n");
        writer.write("    .diff-table tr:hover { background: #f5f5f5; }\n");
        writer.write("    .diff-type { display: inline-block; padding: 3px 8px; margin: 2px; border-radius: 3px; font-size: 11px; font-weight: bold; }\n");
        writer.write("    .diff-type.cost { background: #ffeb3b; color: #333; }\n");
        writer.write("    .diff-type.term { background: #ff9800; color: white; }\n");
        writer.write("    .diff-type.pos { background: #f44336; color: white; }\n");
        writer.write("    .detail-row { background: #fafafa; display: none; }\n");
        writer.write("    .detail-row td { padding: 20px; }\n");
        writer.write("    .comparison { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }\n");
        writer.write("    .comparison-panel { border: 1px solid #ddd; border-radius: 5px; padding: 15px; }\n");
        writer.write("    .comparison-panel h4 { margin-top: 0; padding: 8px; border-radius: 3px; }\n");
        writer.write("    .old-panel h4 { background: #ffebee; color: #c62828; }\n");
        writer.write("    .new-panel h4 { background: #e8f5e9; color: #2e7d32; }\n");
        writer.write("    .result-item { margin: 10px 0; padding: 10px; background: white; border-radius: 3px; }\n");
        writer.write("    .result-label { font-weight: bold; color: #666; margin-bottom: 5px; }\n");
        writer.write("    .result-value { font-family: 'Courier New', monospace; font-size: 13px; word-break: break-all; }\n");
        writer.write("    .toggle-detail { cursor: pointer; color: #2196F3; text-decoration: underline; }\n");
        writer.write("    .toggle-detail:hover { color: #1976D2; }\n");
        writer.write("    details { margin: 10px 0; }\n");
        writer.write("    summary { cursor: pointer; font-weight: bold; padding: 8px; background: #f0f0f0; border-radius: 3px; }\n");
        writer.write("    summary:hover { background: #e0e0e0; }\n");
    }

    private void writeExecutionInfo(BufferedWriter writer) throws IOException {
        writer.write("    <div class=\"info-section\">\n");
        writer.write("      <h2>実行情報</h2>\n");
        writer.write("      <table class=\"info-table\">\n");

        if (execInfo.getStartTime() != null) {
            writer.write("        <tr><th>実行開始時刻</th><td>" + escapeHtml(DATE_FORMAT.format(execInfo.getStartTime())) + "</td></tr>\n");
        }
        if (execInfo.getEndTime() != null) {
            writer.write("        <tr><th>実行終了時刻</th><td>" + escapeHtml(DATE_FORMAT.format(execInfo.getEndTime())) + "</td></tr>\n");
        }
        if (execInfo.getDurationMs() > 0) {
            writer.write("        <tr><th>実行時間</th><td>" + formatDuration(execInfo.getDurationMs()) + "</td></tr>\n");
        }

        writer.write("        <tr><th>Old JAR/Dir</th><td>" + escapeHtml(execInfo.getOldJarPath()) + "</td></tr>\n");
        if (execInfo.getOldJarFiles() != null && !execInfo.getOldJarFiles().isEmpty()) {
            writer.write("        <tr><td colspan=\"2\">");
            writer.write("<details><summary>Old JAR files (" + execInfo.getOldJarFiles().size() + " files)</summary>");
            writer.write("<ul class=\"jar-list\">");
            for (String jar : execInfo.getOldJarFiles()) {
                writer.write("<li>" + escapeHtml(jar) + "</li>");
            }
            writer.write("</ul></details>");
            writer.write("</td></tr>\n");
        }

        writer.write("        <tr><th>New JAR/Dir</th><td>" + escapeHtml(execInfo.getNewJarPath()) + "</td></tr>\n");
        if (execInfo.getNewJarFiles() != null && !execInfo.getNewJarFiles().isEmpty()) {
            writer.write("        <tr><td colspan=\"2\">");
            writer.write("<details><summary>New JAR files (" + execInfo.getNewJarFiles().size() + " files)</summary>");
            writer.write("<ul class=\"jar-list\">");
            for (String jar : execInfo.getNewJarFiles()) {
                writer.write("<li>" + escapeHtml(jar) + "</li>");
            }
            writer.write("</ul></details>");
            writer.write("</td></tr>\n");
        }

        String dataSourceLabel = "Parquet".equals(execInfo.getDataSourceType()) ? "Wiki40b Parquet" : "Wikipedia XML";
        writer.write("        <tr><th>" + dataSourceLabel + "</th><td>" + escapeHtml(execInfo.getDataSourcePath()) + "</td></tr>\n");

        String recordLimit = execInfo.getMaxRecordCount() > 0
                ? execInfo.getMaxRecordCount() + " (制限あり)"
                : "全件処理";
        writer.write("        <tr><th>最大レコード数</th><td>" + escapeHtml(recordLimit) + "</td></tr>\n");

        writer.write("      </table>\n");
        writer.write("    </div>\n");
    }

    private void writeSummary(BufferedWriter writer) throws IOException {
        writer.write("    <h2>処理結果サマリー</h2>\n");
        writer.write("    <div class=\"summary-section\">\n");

        writer.write("      <div class=\"summary-card info\">\n");
        writer.write("        <div class=\"label\">総処理数</div>\n");
        writer.write("        <div class=\"number\">" + execInfo.getTotalProcessed() + "</div>\n");
        writer.write("      </div>\n");

        writer.write("      <div class=\"summary-card warning\">\n");
        writer.write("        <div class=\"label\">差分あり</div>\n");
        writer.write("        <div class=\"number\">" + execInfo.getDifferenceCount() + "</div>\n");
        double diffRate = execInfo.getTotalProcessed() > 0
                ? (double) execInfo.getDifferenceCount() / execInfo.getTotalProcessed() * 100
                : 0;
        writer.write("        <div class=\"label\">" + String.format("%.2f%%", diffRate) + "</div>\n");
        writer.write("      </div>\n");

        writer.write("      <div class=\"summary-card success\">\n");
        writer.write("        <div class=\"label\">一致</div>\n");
        int matchCount = execInfo.getTotalProcessed() - execInfo.getDifferenceCount();
        writer.write("        <div class=\"number\">" + matchCount + "</div>\n");
        double matchRate = execInfo.getTotalProcessed() > 0
                ? (double) matchCount / execInfo.getTotalProcessed() * 100
                : 0;
        writer.write("        <div class=\"label\">" + String.format("%.2f%%", matchRate) + "</div>\n");
        writer.write("      </div>\n");

        writer.write("      <div class=\"summary-card info\">\n");
        writer.write("        <div class=\"label\">スキップ</div>\n");
        writer.write("        <div class=\"number\">" + execInfo.getSkippedCount() + "</div>\n");
        writer.write("      </div>\n");

        writer.write("    </div>\n");
    }

    private void writeDiffDetails(BufferedWriter writer) throws IOException {
        writer.write("    <h2>差分詳細 (" + diffCount + "件)</h2>\n");

        if (diffCount == 0) {
            writer.write("    <p>差分はありません。</p>\n");
            return;
        }

        writer.write("    <table class=\"diff-table\">\n");
        writer.write("      <thead>\n");
        writer.write("        <tr>\n");
        writer.write("          <th style=\"width: 50px;\">#</th>\n");
        writer.write("          <th>タイトル</th>\n");
        writer.write("          <th style=\"width: 200px;\">差分タイプ</th>\n");
        writer.write("          <th style=\"width: 100px;\">詳細</th>\n");
        writer.write("        </tr>\n");
        writer.write("      </thead>\n");
        writer.write("      <tbody>\n");

        flush();
        writeDiffBodyFromTempFile(writer);

        writer.write("      </tbody>\n");
        writer.write("    </table>\n");
    }

    private void writeDiffDetail(BufferedWriter writer, String text,
                                 AnalyzeResult[] oldResult, AnalyzeResult[] newResult) throws IOException {
        writer.write("            <div class=\"comparison\">\n");

        // Old panel
        writer.write("              <div class=\"comparison-panel old-panel\">\n");
        writer.write("                <h4>OLD</h4>\n");
        writeAnalyzeResults(writer, oldResult);
        writer.write("              </div>\n");

        // New panel
        writer.write("              <div class=\"comparison-panel new-panel\">\n");
        writer.write("                <h4>NEW</h4>\n");
        writeAnalyzeResults(writer, newResult);
        writer.write("              </div>\n");

        writer.write("            </div>\n");

        // 元テキスト
        if (text != null && !text.isEmpty()) {
            writer.write("            <details style=\"margin-top: 15px;\">\n");
            writer.write("              <summary>元テキスト</summary>\n");
            writer.write("              <div style=\"padding: 10px; background: #f9f9f9; margin-top: 10px; white-space: pre-wrap; font-size: 13px;\">");
            writer.write(escapeHtml(text.substring(0, Math.min(500, text.length()))));
            if (text.length() > 500) {
                writer.write("...(省略)");
            }
            writer.write("</div>\n");
            writer.write("            </details>\n");
        }
    }

    private void writeAnalyzeResults(BufferedWriter writer, AnalyzeResult[] results) throws IOException {
        for (int i = 0; i < Math.min(results.length, RESULT_SIZE); i++) {
            AnalyzeResult result = results[i];

            writer.write("                <div class=\"result-item\">\n");
            writer.write("                  <div class=\"result-label\">Terms:</div>\n");
            writer.write("                  <div class=\"result-value\">" + escapeHtml(result.getTermList().toString()) + "</div>\n");
            writer.write("                </div>\n");

            writer.write("                <div class=\"result-item\">\n");
            writer.write("                  <div class=\"result-label\">POS:</div>\n");
            writer.write("                  <div class=\"result-value\">" + escapeHtml(result.getPosList().toString()) + "</div>\n");
            writer.write("                </div>\n");

            writer.write("                <div class=\"result-item\">\n");
            writer.write("                  <div class=\"result-label\">Total Cost:</div>\n");
            writer.write("                  <div class=\"result-value\">" + result.getTotalCost() + "</div>\n");
            writer.write("                </div>\n");
        }
    }

    private void writeHtmlFooter(BufferedWriter writer) throws IOException {
        writer.write("    <script>\n");
        writer.write("      function toggleDetail(id) {\n");
        writer.write("        var element = document.getElementById(id);\n");
        writer.write("        if (element.style.display === 'table-row') {\n");
        writer.write("          element.style.display = 'none';\n");
        writer.write("        } else {\n");
        writer.write("          element.style.display = 'table-row';\n");
        writer.write("        }\n");
        writer.write("      }\n");
        writer.write("    </script>\n");
        writer.write("  </div>\n");
        writer.write("</body>\n");
        writer.write("</html>\n");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d時間%d分%d秒 (%d ms)", hours, minutes % 60, seconds % 60, ms);
        } else if (minutes > 0) {
            return String.format("%d分%d秒 (%d ms)", minutes, seconds % 60, ms);
        } else {
            return String.format("%d秒 (%d ms)", seconds, ms);
        }
    }

    @Override
    public void close() throws IOException {
        IOException closeException = null;
        if (diffWriter != null) {
            try {
                diffWriter.close();
            } catch (IOException e) {
                closeException = e;
            } finally {
                diffWriter = null;
            }
        }
        if (diffTempFile != null) {
            try {
                Files.deleteIfExists(diffTempFile);
            } catch (IOException e) {
                if (closeException == null) {
                    closeException = e;
                }
            } finally {
                diffTempFile = null;
            }
        }
        if (closeException != null) {
            throw closeException;
        }
    }

    private void ensureDiffWriter() throws IOException {
        if (diffWriter != null) {
            return;
        }
        diffTempFile = Files.createTempFile("lucene-gosen-diff-", ".html");
        diffWriter = newUtf8Writer(
                diffTempFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private BufferedWriter newUtf8Writer(Path path, StandardOpenOption... options) throws IOException {
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path, options), encoder));
    }

    private void writeDiffRecord(BufferedWriter writer, int index, WikipediaModel model,
                                 AnalyzeResult[] oldResult, AnalyzeResult[] newResult) throws IOException {
        String rowId = "row-" + index;
        String detailId = "detail-" + index;

        writer.write("        <tr id=\"" + rowId + "\">\n");
        writer.write("          <td>" + index + "</td>\n");
        writer.write("          <td>" + escapeHtml(model.getTitle()) + "</td>\n");
        writer.write("          <td>");
        List<String> diffTypes = detectDiffTypes(oldResult, newResult);
        for (String type : diffTypes) {
            writer.write("<span class=\"diff-type " + type + "\">" + type.toUpperCase() + "</span> ");
        }
        writer.write("</td>\n");
        writer.write("          <td><span class=\"toggle-detail\" onclick=\"toggleDetail('" + detailId + "')\">表示/非表示</span></td>\n");
        writer.write("        </tr>\n");

        writer.write("        <tr id=\"" + detailId + "\" class=\"detail-row\">\n");
        writer.write("          <td colspan=\"4\">\n");
        writeDiffDetail(writer, model.getText(), oldResult, newResult);
        writer.write("          </td>\n");
        writer.write("        </tr>\n");
    }

    private void writeDiffBodyFromTempFile(BufferedWriter writer) throws IOException {
        if (diffTempFile == null) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(diffTempFile, StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                writer.write(buffer, 0, read);
            }
        }
    }
}
