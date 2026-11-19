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
 * Builds the three different self-contained HTML reports.
 * Each report = HTML template + shared JavaScript + the data embedded as JSON.
 * Test-case files (ID / Steps / Expected Result / Status) get their own three reports.
 */
@Service
public class ReportService {

    public record Definition(int no, String name, String description, String template) {
    }

    public static final List<Definition> GENERIC = List.of(
            new Definition(1, "Executive Dashboard",
                    "Key numbers and four charts that update with quick filters. Best for a fast overview.",
                    "report1.html"),
            new Definition(2, "Data Explorer",
                    "Clean table of your data with date, number and category filters, search, sorting and a chart builder.",
                    "report2.html"),
            new Definition(3, "Insights & Data Quality",
                    "Column-by-column picture of your data: distributions, correlations, outliers and data-quality checks.",
                    "report3.html"));

    public static final List<Definition> TEST_CASES = List.of(
            new Definition(1, "Execution Summary",
                    "Pass / fail at a glance: status donut, pass rate, results per section and the list of failed test cases.",
                    "tc1.html"),
            new Definition(2, "Test Case Explorer",
                    "Every test case with its full ID, title, precondition, steps and expected result. Filter by status or section, or search.",
                    "tc2.html"),
            new Definition(3, "Coverage & Quality",
                    "Section-by-section coverage, results per module, and test-design checks such as missing steps or duplicate IDs.",
                    "tc3.html"));

    private final ObjectMapper mapper;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public ReportService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static List<Definition> definitionsFor(AnalyzedTable table) {
        return table.testCase() != null ? TEST_CASES : GENERIC;
    }

    public String generate(AnalyzedTable table, Definition def) throws IOException {
        String html = load(def.template());
        String common = load("common.js");
        String tc = def.template().startsWith("tc") ? load("tc-common.js") : "";

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
        data.put("columns", table.columns());
        data.put("rows", table.rows());

        String json = toSafeJson(data);

        // Order matters: user data goes in LAST so its content is never scanned for placeholders.
        return html
                .replace("/*__COMMON__*/", common)
                .replace("/*__TC__*/", tc)
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
