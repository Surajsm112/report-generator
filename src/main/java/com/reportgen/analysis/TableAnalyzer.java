package com.reportgen.analysis;

import com.reportgen.model.AnalyzedTable;
import com.reportgen.model.ColumnInfo;
import com.reportgen.model.ColumnType;
import com.reportgen.model.RawTable;
import com.reportgen.model.TestCaseInfo;
import com.reportgen.parse.ValueParsers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Decides the type of every column and converts the string cells into typed values. */
public class TableAnalyzer {

    /** A column is NUMBER/DATE if at least this share of its non-empty cells parse. */
    private static final double MIN_MATCH = 0.95;
    private static final int MAX_CATEGORIES = 25;

    /** Columns named like these hold identifiers, not quantities, even if the values are digits. */
    private static final Pattern ID_LIKE =
            Pattern.compile("(?i)(^|[^a-z])(id|phone|mobile|zip|pincode|pin|code|number|no)([^a-z]|$)");

    public AnalyzedTable analyze(String fileName, RawTable raw) {
        if (raw.layout() != null) {
            // A form / worksheet: nothing to type. The reports show the sheet as laid out.
            String layoutTitle = fileName == null ? "Report" : fileName.replaceFirst("\\.[^.]+$", "");
            List<ColumnInfo> letters = raw.layout().columns().stream()
                    .map(l -> new ColumnInfo(l, ColumnType.TEXT, 0, 0, null, null, null, null, null, null, null)).toList();
            return new AnalyzedTable(layoutTitle, fileName, raw.sheetName(), raw.meta(), letters, List.of(),
                    raw.truncated(), raw.totalRows(), null, raw.layout());
        }
        int columnCount = raw.headers().size();
        int rowCount = raw.rows().size();

        List<ColumnInfo> columns = new ArrayList<>();
        List<List<Object>> typedRows = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            typedRows.add(new ArrayList<>(columnCount));
        }

        for (int c = 0; c < columnCount; c++) {
            String name = raw.headers().get(c);
            List<String> values = new ArrayList<>(rowCount);
            for (List<String> row : raw.rows()) {
                values.add(row.get(c));
            }
            boolean forceCategory = name.equals(raw.sectionColumn());
            ColumnInfo info = analyzeColumn(name, values, typedRows, c, forceCategory);
            columns.add(info);
        }

        String title = fileName == null ? "Report" : fileName.replaceFirst("\\.[^.]+$", "");
        TestCaseInfo testCase = TestCaseDetector.detect(columns, typedRows, raw.sectionColumn());
        return new AnalyzedTable(title, fileName, raw.sheetName(), raw.meta(), columns, typedRows,
                raw.truncated(), raw.totalRows(), testCase, null);
    }

    private ColumnInfo analyzeColumn(String name, List<String> values, List<List<Object>> typedRows, int c,
                                     boolean forceCategory) {
        List<String> present = values.stream().filter(v -> v != null).toList();
        int missing = values.size() - present.size();
        Set<String> distinctValues = new HashSet<>(present);
        int distinct = distinctValues.size();

        if (forceCategory) {
            for (int r = 0; r < values.size(); r++) {
                typedRows.get(r).add(values.get(r));
            }
            return new ColumnInfo(name, ColumnType.CATEGORY, missing, distinct, null, null, null, null, null, null, null);
        }

        // 1. NUMBER?
        long numeric = present.stream().filter(v -> ValueParsers.parseNumber(v) != null).count();
        boolean idLike = ID_LIKE.matcher(name).find();
        if (!present.isEmpty() && numeric >= present.size() * MIN_MATCH && !idLike) {
            return numberColumn(name, values, typedRows, c, missing, distinct);
        }

        // 2. DATE? Pick the format that parses the most values.
        int bestFormat = -1;
        long bestCount = 0;
        if (!present.isEmpty() && numeric < present.size() * 0.5) {
            for (int f = 0; f < ValueParsers.formatCount(); f++) {
                final int fi = f;
                long ok = present.stream().filter(v -> ValueParsers.parseDate(v, fi) != null).count();
                if (ok > bestCount) {
                    bestCount = ok;
                    bestFormat = f;
                }
            }
        }
        if (bestFormat >= 0 && bestCount >= present.size() * MIN_MATCH) {
            return dateColumn(name, values, typedRows, c, missing, distinct, bestFormat);
        }

        // 3. CATEGORY or TEXT (identifier-like digit columns such as "Order ID" stay TEXT)
        boolean digitsId = idLike && !present.isEmpty() && numeric >= present.size() * MIN_MATCH;
        ColumnType type = (!digitsId && distinct > 0 && distinct <= MAX_CATEGORIES
                && (distinct <= 8 || distinct <= present.size() * 0.5))
                ? ColumnType.CATEGORY : ColumnType.TEXT;
        for (int r = 0; r < values.size(); r++) {
            typedRows.get(r).add(values.get(r));
        }
        return new ColumnInfo(name, type, missing, distinct, null, null, null, null, null, null, null);
    }

    private ColumnInfo numberColumn(String name, List<String> values, List<List<Object>> typedRows,
                                    int c, int missing, int distinct) {
        List<Double> nums = new ArrayList<>();
        for (int r = 0; r < values.size(); r++) {
            Double d = ValueParsers.parseNumber(values.get(r));
            typedRows.get(r).add(d);
            if (d != null) {
                nums.add(d);
            }
        }
        double[] sorted = nums.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double sum = Arrays.stream(sorted).sum();
        double median = sorted.length == 0 ? 0
                : sorted.length % 2 == 1 ? sorted[sorted.length / 2]
                : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
        // values that did not parse count as missing for a number column
        int bad = (values.size() - missing) - nums.size();
        return new ColumnInfo(name, ColumnType.NUMBER, missing + bad, distinct,
                sorted.length == 0 ? null : sorted[0],
                sorted.length == 0 ? null : sorted[sorted.length - 1],
                sorted.length == 0 ? null : sum / sorted.length,
                median, sum, null, null);
    }

    private ColumnInfo dateColumn(String name, List<String> values, List<List<Object>> typedRows,
                                  int c, int missing, int distinct, int format) {
        LocalDate min = null;
        LocalDate max = null;
        int bad = 0;
        for (int r = 0; r < values.size(); r++) {
            LocalDate d = ValueParsers.parseDate(values.get(r), format);
            if (values.get(r) != null && d == null) {
                bad++;
            }
            typedRows.get(r).add(d == null ? null : d.toString());
            if (d != null) {
                if (min == null || d.isBefore(min)) {
                    min = d;
                }
                if (max == null || d.isAfter(max)) {
                    max = d;
                }
            }
        }
        return new ColumnInfo(name, ColumnType.DATE, missing + bad, distinct,
                null, null, null, null, null,
                min == null ? null : min.toString(), max == null ? null : max.toString());
    }
}
