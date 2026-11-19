package com.reportgen.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportgen.model.AnalyzedTable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the self-contained HTML reports.
 * Each report = HTML template + shared JavaScript + the data embedded as JSON.
 * The kind of file decides the report set: ordinary data, test cases, or a form-style worksheet.
 */
@Service
public class ReportService {

    public record Definition(int no, String name, String description, String template, String icon) {
    }

    public static final List<Definition> GENERIC = List.of(
            new Definition(1, "Executive Dashboard",
                    "Key numbers and four charts that update with quick filters. Best for a fast overview.",
                    "report1.html", "📊"),
            new Definition(2, "Data Explorer",
                    "Clean table of your data with date, number and category filters, search, sorting and a chart builder.",
                    "report2.html", "🔎"),
            new Definition(3, "Insights & Data Quality",
                    "Column-by-column picture of your data: distributions, correlations, outliers and data-quality checks.",
                    "report3.html", "🧪"),
            new Definition(4, "Trends",
                    "How the numbers move over time: area charts, running total, change from the previous period and weekday pattern.",
                    "report4.html", "📈"),
            new Definition(5, "Categories",
                    "Every grouping column ranked with bar charts, share doughnuts and tables with share bars.",
                    "report5.html", "🧩"),
            new Definition(6, "Distributions",
                    "How the numbers are spread: histograms, range bars, outliers, a scatter plot and a correlation heat grid.",
                    "report6.html", "🎯"),
            new Definition(7, "Leaderboard",
                    "Top and bottom rows by the main measure, with a colour heat map of totals by category and month.",
                    "report7.html", "🏆"));

    public static final List<Definition> TEST_CASES = List.of(
            new Definition(1, "Execution Summary",
                    "Pass / fail at a glance: status donut, pass rate, results per section and every test case with its steps.",
                    "tc1.html", "✅"),
            new Definition(2, "Test Case Explorer",
                    "Every test case with its full ID, title, precondition, steps and expected result. Filter by status or section, or search.",
                    "tc2.html", "🔍"),
            new Definition(3, "Coverage & Quality",
                    "Section-by-section coverage with test cases, results per module, and design checks such as missing steps.",
                    "tc3.html", "🛡️"),
            new Definition(4, "Section Scorecard",
                    "Which sections are strong and which are weak: ranked bars, a radar, a polar chart and a colour heat map.",
                    "tc4.html", "🏅"),
            new Definition(5, "Issues & Risk",
                    "Everything that failed, is blocked or has no result, by section and type, with the steps to retest.",
                    "tc5.html", "⚠️"),
            new Definition(6, "Test Document",
                    "A clean, printable document for clients: cover page, contents and one table per section. Print or save as PDF.",
                    "tc6.html", "📄"),
            new Definition(7, "Visual Dashboard",
                    "Seven charts on one page: results, pass-rate gauge, sections, modules, detail level and progress.",
                    "tc7.html", "🌈"));

    /** Forms and worksheets (labels and values placed around the sheet) are shown as laid out. */
    public static final List<Definition> WORKSHEET = List.of(
            new Definition(1, "Key Figures",
                    "Every label and its value from the sheet, grouped under the sheet's own headings. Notes are listed separately.",
                    "sheet1.html", "🗂️"),
            new Definition(2, "Sheet View",
                    "The sheet exactly as it is laid out, with Excel row numbers and column letters. Search to find any cell.",
                    "sheet2.html", "🧾"),
            new Definition(3, "Number Series",
                    "Rows of numbers such as yearly figures drawn as charts, with their values listed.",
                    "sheet3.html", "📉"));

    /** Templates that use the shared gradient / tile / heat-colour helpers. */
    private static final java.util.regex.Pattern VISUAL = java.util.regex.Pattern.compile("^(report[4-7]|tc[4-7])\\.html$");

    private final ObjectMapper mapper;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public ReportService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static List<Definition> definitionsFor(AnalyzedTable table) {
        if (table.layout() != null) {
            return WORKSHEET;
        }
        return table.testCase() != null ? TEST_CASES : GENERIC;
    }

    public String generate(AnalyzedTable table, Definition def) throws IOException {
        String html = load(def.template());
        String common = load("common.js");
        String tc = def.template().startsWith("tc") ? load("tc-common.js") : "";
        String sheet = def.template().startsWith("sheet") ? load("sheet-common.js") : "";
        String viz = VISUAL.matcher(def.template()).matches() ? load("viz.js") : "";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", table.title());
        data.put("fileName", table.fileName());
        data.put("sheetName", table.sheetName());
        data.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")));
        data.put("rowCount", table.rows().size());
        data.put("totalRows", table.totalRows());
        data.put("truncated", table.truncated());
        data.put("meta", table.meta());
        data.put("testCase", table.testCase());
        data.put("layout", table.layout());
        data.put("columns", table.columns());
        data.put("rows", table.rows());

        String json = toSafeJson(data);

        // Order matters: user data goes in LAST so its content is never scanned for placeholders.
        return html
                .replace("/*__COMMON__*/", common)
                .replace("/*__TC__*/", tc)
                .replace("/*__SHEET__*/", sheet)
                .replace("/*__VIZ__*/", viz)
                .replace("__TITLE__", HtmlUtils.htmlEscape(table.title()))
                .replace("/*__DATA__*/null", json);
    }

    /** JSON that is safe to place inside a script tag. */
    private String toSafeJson(Object data) throws JsonProcessingException {
        return mapper.writeValueAsString(data)
                .replace("</", "<\\/")
                .replace(" ", "\\u2028")
                .replace(" ", "\\u2029");
    }

    private String load(String name) throws IOException {
        String cached = templateCache.get(name);
        if (cached != null) {
            return cached;
        }
        try (InputStream in = getClass().getResourceAsStream("/report-templates/" + name)) {
            if (in == null) {
                throw new IOException("Missing template " + name);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            templateCache.put(name, text);
            return text;
        }
    }
}
