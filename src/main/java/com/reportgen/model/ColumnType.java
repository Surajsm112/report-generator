package com.reportgen.model;

/** What a column holds. The type decides which filter and which chart a column gets. */
public enum ColumnType {
    NUMBER,    // amounts, counts, ratings  -> range filter, sum/avg, histogram
    DATE,      // dates                     -> date-range filter, trend line
    CATEGORY,  // few repeating labels      -> dropdown filter, bar/pie
    TEXT       // free text / ids           -> "contains" filter
}
