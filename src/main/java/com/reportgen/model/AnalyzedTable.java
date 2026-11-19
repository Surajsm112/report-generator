package com.reportgen.model;

import java.util.List;
import java.util.Map;

/**
 * Typed data: numbers are Double, dates are ISO strings, everything else is String, empty is null.
 * testCase is non-null when the file looks like a list of test cases with pass/fail results.
 */
public record AnalyzedTable(
        String title,
        String fileName,
        String sheetName,
        Map<String, List<String>> meta,
        List<ColumnInfo> columns,
        List<List<Object>> rows,
        boolean truncated,
        int totalRows,
        TestCaseInfo testCase) {
}
