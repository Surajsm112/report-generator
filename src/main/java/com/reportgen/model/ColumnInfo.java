package com.reportgen.model;

/**
 * Description of one column. min/max/mean/median/sum are filled for NUMBER;
 * minDate/maxDate are filled for DATE (ISO yyyy-MM-dd). Jackson writes this straight into the report.
 */
public record ColumnInfo(
        String name,
        ColumnType type,
        int missing,
        int distinct,
        Double min,
        Double max,
        Double mean,
        Double median,
        Double sum,
        String minDate,
        String maxDate) {
}
