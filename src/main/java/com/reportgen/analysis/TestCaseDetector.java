package com.reportgen.analysis;

import com.reportgen.model.ColumnInfo;
import com.reportgen.model.TestCaseInfo;
import com.reportgen.parse.StatusVocabulary;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Recognises a sheet of test cases: ID / Title / Steps / Expected Result / Status (PASS, FAIL ...). */
public final class TestCaseDetector {

    private static final Pattern STATUS = Pattern.compile(
            "(?i)^(test )?(status|result|execution status|execution result|actual status|outcome|pass ?/ ?fail)$");
    private static final Pattern ID = Pattern.compile("(?i)^((test ?case|tc|case|test) ?)?(id|no|number|#)$|^tc ?id$|^test ?case ?id$");
    private static final Pattern TITLE = Pattern.compile("(?i)(title|scenario|test ?case( name)?|summary|name|description)");
    private static final Pattern PRE = Pattern.compile("(?i)pre-?\\s?condition|prerequisite|setup");
    private static final Pattern STEPS = Pattern.compile("(?i)(steps?|procedure|action|how to)");
    private static final Pattern EXPECTED = Pattern.compile("(?i)expected");

    private TestCaseDetector() {
    }

    /** Returns null when the sheet is not a test-case sheet. */
    public static TestCaseInfo detect(List<ColumnInfo> columns, List<List<Object>> rows, String sectionColumn) {
        int status = findStatus(columns, rows);
        if (status < 0) {
            return null;
        }
        int id = -1, title = -1, pre = -1, steps = -1, expected = -1, section = -1;
        for (int c = 0; c < columns.size(); c++) {
            if (c == status) {
                continue;
            }
            String name = columns.get(c).name().trim();
            String lower = name.toLowerCase(Locale.ROOT);
            if (sectionColumn != null && name.equals(sectionColumn)) {
                section = c;
            } else if (id < 0 && ID.matcher(lower).find()) {
                id = c;
            } else if (expected < 0 && EXPECTED.matcher(lower).find()) {
                expected = c;
            } else if (pre < 0 && PRE.matcher(lower).find()) {
                pre = c;
            } else if (steps < 0 && STEPS.matcher(lower).find()) {
                steps = c;
            } else if (title < 0 && TITLE.matcher(lower).find()) {
                title = c;
            }
        }
        // Full test-case sheet: has steps or an expected result next to an ID / title.
        boolean detailed = (steps >= 0 || expected >= 0) && (id >= 0 || title >= 0);
        // Minimal sheet: only a test name and a clear PASS / FAIL column, e.g. "Login success | PASS".
        boolean minimal = (title >= 0 || id >= 0) && resultRatio(rows, status) >= 0.8;
        if (!detailed && !minimal) {
            return null;
        }
        return new TestCaseInfo(id, title, pre, steps, expected, status, section);
    }

    /** Share of filled cells in the column that are test results (pass, fail, blocked ...). */
    private static double resultRatio(List<List<Object>> rows, int col) {
        int filled = 0;
        int hits = 0;
        for (List<Object> r : rows) {
            if (r.get(col) instanceof String s && !s.isBlank()) {
                filled++;
                if (StatusVocabulary.isTestResult(s)) {
                    hits++;
                }
            }
        }
        return filled == 0 ? 0 : (double) hits / filled;
    }

    private static int findStatus(List<ColumnInfo> columns, List<List<Object>> rows) {
        int best = -1;
        double bestRatio = 0;
        for (int c = 0; c < columns.size(); c++) {
            int filled = 0;
            int hits = 0;
            for (List<Object> r : rows) {
                Object v = r.get(c);
                if (v instanceof String s && !s.isBlank()) {
                    filled++;
                    if (StatusVocabulary.isTestResult(s)) {
                        hits++;
                    }
                }
            }
            if (filled == 0) {
                continue;
            }
            double ratio = (double) hits / filled;
            boolean named = STATUS.matcher(columns.get(c).name().trim()).matches();
            // A named Status column may be mostly empty or hold a few unusual words; others must be clearly pass/fail.
            if ((named && ratio >= 0.6) || ratio >= 0.9) {
                if (ratio > bestRatio || (named && best >= 0 && !STATUS.matcher(columns.get(best).name()).matches())) {
                    best = c;
                    bestRatio = ratio;
                }
            }
        }
        return best;
    }
}
