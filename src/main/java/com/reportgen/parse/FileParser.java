package com.reportgen.parse;

import com.reportgen.model.RawTable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads an uploaded Excel / CSV / text file into a grid of cells and hands it to {@link GridReader}. */
public class FileParser {

    /** Browsers struggle with huge reports, so we keep the first rows only. */
    public static final int MAX_ROWS = 50_000;
    private static final int MAX_COLUMNS = 200;
    private static final int MAX_GRID_ROWS = 300_000;

    public RawTable parse(String fileName, byte[] content) throws IOException {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(content);
        }
        return parseDelimited(content);
    }

    // ---------- CSV / TSV / TXT ----------

    private RawTable parseDelimited(byte[] content) throws IOException {
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1); // strip BOM written by Excel
        }
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(false)
                .setTrim(true)
                .build();

        List<List<String>> grid = new ArrayList<>();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                if (grid.size() >= MAX_GRID_ROWS) {
                    break;
                }
                List<String> cells = new ArrayList<>(record.size());
                for (String v : record) {
                    cells.add(v);
                }
                grid.add(cells);
            }
        }
        return GridReader.read(grid, null, MAX_ROWS);
    }

    /** Picks the delimiter that appears most often in the first line (outside quotes). */
    static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        char[] candidates = {',', ';', '\t', '|'};
        char best = ',';
        int bestCount = 0;
        for (char c : candidates) {
            int count = 0;
            boolean inQuotes = false;
            for (int i = 0; i < first.length(); i++) {
                char ch = first.charAt(i);
                if (ch == '"') {
                    inQuotes = !inQuotes;
                } else if (ch == c && !inQuotes) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = c;
            }
        }
        return best;
    }

    // ---------- Excel ----------

    private RawTable parseExcel(byte[] content) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            // Use the sheet that holds the most data (a file often has a small "Summary" sheet next to the real one).
            Sheet best = null;
            long bestCount = -1;
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                long count = 0;
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() != CellType.BLANK && cell.getCellType() != CellType.ERROR) {
                            String v = cellText(cell, formatter, evaluator);
                            if (v != null && !v.isBlank()) {
                                count++;
                            }
                        }
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    best = sheet;
                }
            }
            if (best == null || bestCount <= 0) {
                throw new IllegalArgumentException("The file is empty.");
            }

            List<List<String>> grid = new ArrayList<>();
            int lastRow = best.getLastRowNum();
            for (int r = best.getFirstRowNum(); r <= lastRow && grid.size() < MAX_GRID_ROWS; r++) {
                Row row = best.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int last = Math.min(row.getLastCellNum(), MAX_COLUMNS);
                    for (int c = 0; c < last; c++) {
                        cells.add(cellText(row.getCell(c), formatter, evaluator));
                    }
                }
                grid.add(cells);
            }
            return GridReader.read(grid, best.getSheetName(), MAX_ROWS);
        }
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate()
                        .format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            double d = cell.getNumericCellValue();
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        return formatter.formatCellValue(cell, evaluator);
    }
}
