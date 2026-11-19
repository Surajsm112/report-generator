package com.reportgen.model;

import java.util.List;

/**
 * A worksheet shown exactly as laid out (used for forms, models and other sheets that are not a table).
 *
 * @param columns    Excel column letters of the columns that hold data (A, C, E ...)
 * @param rowNumbers Excel row numbers of the rows that hold data
 * @param cells      cell text exactly as Excel shows it, empty string for empty cells
 */
public record Layout(List<String> columns, List<Integer> rowNumbers, List<List<String>> cells, boolean truncated) {
}
