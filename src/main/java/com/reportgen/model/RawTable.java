package com.reportgen.model;

import java.util.List;
import java.util.Map;

/**
 * Parsed file before any typing.
 *
 * @param headers       real column names (empty / unused sheet columns are already removed)
 * @param rows          data rows of strings, null = empty cell
 * @param meta          information found above or between tables, e.g. Ticket, Screen Name, Date
 * @param sheetName     the Excel sheet that was used (null for CSV / text)
 * @param sectionColumn name of the added "Section" column, or null when the file has one table only
 */
public record RawTable(
        List<String> headers,
        List<List<String>> rows,
        Map<String, List<String>> meta,
        String sheetName,
        String sectionColumn,
        boolean truncated,
        int totalRows) {
}
