package com.reportgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportgen.analysis.TableAnalyzer;
import com.reportgen.model.AnalyzedTable;
import com.reportgen.model.ColumnType;
import com.reportgen.model.RawTable;
import com.reportgen.parse.FileParser;
import com.reportgen.report.ReportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineTest {

    private final FileParser parser = new FileParser();
    private final TableAnalyzer analyzer = new TableAnalyzer();

    private AnalyzedTable analyze(String name, String text) throws Exception {
        RawTable raw = parser.parse(name, text.getBytes(StandardCharsets.UTF_8));
        return analyzer.analyze(name, raw);
    }

    private static void row(Sheet sheet, int r, String... values) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                row.createCell(i).setCellValue(values[i]);
            }
        }
    }

    /** A sheet shaped like a real test-case workbook: title block, several sections, different headers, empty columns. */
    private byte[] testCaseWorkbook() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Test Cases");
            row(s, 0, "26/07/2026");
            row(s, 1, "Ticket", "https://jira.example.com/browse/TMRI-1");
            row(s, 2, "Screen Name", "Login");
            row(s, 4, "Test Cases: Login\nBasic");
            row(s, 5, "ID", "Title", "Precondition", "Steps", "Expected Result", "Status");
            row(s, 6, "TC-L-01", "Valid login", "User exists", "Enter user and password", "Home page opens", "PASS");
            row(s, 7, "TC-L-02", "Wrong password", null, "Enter wrong password", "Error shown", "FAIL");
            row(s, 8, "TC-L-03", "UI check", null, null, null, "PASS");
            row(s, 10, "Fetch");
            row(s, 11, "ID", "Title", "Steps", "Expected Result", "Status");
            row(s, 12, "TC-F-01", "List", "Open list", "List shown", "PASS");
            row(s, 13, "TC-F-02", "Paging", "Click next", "Next page", "BLOCKED");
            row(s, 15, "Update Status / Approval");
            row(s, 16, "ID", "Title", "Precondition", "Steps", "Expected Result"); // Status header is missing
            row(s, 17, "TC-U-01", "Approve", "Pending item", "Click approve", "Status becomes approved", "PASS");
            row(s, 18, "TC-U-02", "Reject", "Pending item", "Click reject", "Status becomes rejected", "PASS");
            // an empty styled cell far to the right (this is what makes Excel report 26 columns)
            CellStyle style = wb.createCellStyle();
            Cell far = s.getRow(5).createCell(25);
            far.setCellStyle(style);
            Sheet summary = wb.createSheet("Summary");
            row(summary, 0, "Item", "Value");
            row(summary, 1, "Total test cases", "3");
            wb.write(out);
        }
        return out.toByteArray();
    }

    @Test
    void readsSectionedTestCaseSheetWithRealHeaders() throws Exception {
        RawTable raw = parser.parse("cases.xlsx", testCaseWorkbook());
        assertEquals(List.of("ID", "Title", "Precondition", "Steps", "Expected Result", "Status", "Section"), raw.headers());
        assertEquals("Test Cases", raw.sheetName());
        assertEquals(7, raw.rows().size());
        assertTrue(raw.headers().stream().noneMatch(h -> h.startsWith("Column")), "no ghost columns");
        assertEquals("Login", raw.meta().get("Screen Name").get(0));
        assertEquals("https://jira.example.com/browse/TMRI-1", raw.meta().get("Ticket").get(0));
        assertEquals("26/07/2026", raw.meta().get("Date").get(0));
        // section names come from the title rows ("Test Cases:" prefix removed, line break joined)
        assertEquals("Login – Basic", raw.rows().get(0).get(6));
        assertEquals("Fetch", raw.rows().get(3).get(6));
        assertEquals("Update Status / Approval", raw.rows().get(5).get(6));
        // the table whose Status header was missing still gets its statuses
        assertEquals("PASS", raw.rows().get(5).get(5));
        assertNull(raw.rows().get(3).get(2), "section without a Precondition column leaves it empty");
    }

    @Test
    void recognisesTestCaseSheetAndStatusColumn() throws Exception {
        AnalyzedTable t = analyzer.analyze("cases.xlsx", parser.parse("cases.xlsx", testCaseWorkbook()));
        assertNotNull(t.testCase());
        assertEquals(0, t.testCase().id());
        assertEquals(1, t.testCase().title());
        assertEquals(3, t.testCase().steps());
        assertEquals(4, t.testCase().expected());
        assertEquals(5, t.testCase().status());
        assertEquals(6, t.testCase().section());
        assertEquals(ColumnType.CATEGORY, t.columns().get(6).type());
        assertEquals(ColumnType.CATEGORY, t.columns().get(5).type());
    }

    @Test
    void minimalSheetWithOnlyNameAndResultIsATestCaseSheet() throws Exception {
        String csv = "Test Case,Status\nLogin success,PASS\nLogin fail,FAIL\nLogout,PASS\n";
        AnalyzedTable t = analyze("login.csv", csv);
        assertNotNull(t.testCase());
        assertEquals(0, t.testCase().title());
        assertEquals(1, t.testCase().status());
        assertEquals(-1, t.testCase().steps());
        assertEquals(-1, t.testCase().expected());
        assertEquals(3, t.rows().size());
    }

    @Test
    void salesStyleDataIsNotTreatedAsTestCases() throws Exception {
        String csv = "Date,Region,Status,Amount\n2026-01-01,North,Open,10\n2026-01-02,South,Closed,20\n2026-01-03,East,Open,30\n";
        assertNull(analyze("sales.csv", csv).testCase());
    }

    @Test
    void readsExcelWorkbookWithDatesAndNumbers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sales");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd-mmm-yyyy"));
            String[] regions = {"North", "South", "North", "East"};
            String[] header = {"Date", "Region", "Amount"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
            }
            for (int r = 0; r < regions.length; r++) {
                Row row = sheet.createRow(r + 1);
                Cell d = row.createCell(0);
                d.setCellValue(LocalDate.of(2026, 3, 1 + r));
                d.setCellStyle(dateStyle);
                row.createCell(1).setCellValue(regions[r]);
                row.createCell(2).setCellValue(100.5 * (r + 1));
            }
            wb.write(out);
        }
        AnalyzedTable t = analyzer.analyze("book.xlsx", parser.parse("book.xlsx", out.toByteArray()));
        assertEquals(4, t.rows().size());
        assertEquals(ColumnType.DATE, t.columns().get(0).type());
        assertEquals("2026-03-01", t.columns().get(0).minDate());
        assertEquals(ColumnType.CATEGORY, t.columns().get(1).type());
        assertEquals(ColumnType.NUMBER, t.columns().get(2).type());
        assertEquals(1005.0, t.columns().get(2).sum(), 0.001);
    }

    @Test
    void titleRowAboveHeaderIsIgnoredAsData() throws Exception {
        String csv = "Monthly sales report\n\nDate,Region,Amount\n2026-01-01,North,10\n2026-01-02,South,20\n";
        AnalyzedTable t = analyze("m.csv", csv);
        assertEquals(List.of("Date", "Region", "Amount"), t.columns().stream().map(c -> c.name()).toList());
        assertEquals(2, t.rows().size());
    }

    @Test
    void detectsTypesInCsv() throws Exception {
        String csv = "Order ID,Date,Region,Amount,Notes\n"
                + "1001,2026-01-05,North,\"1,200.50\",first order here\n"
                + "1002,2026-01-09,South,300,second one\n"
                + "1003,2026-02-11,North,450,third one\n"
                + "1004,2026-02-15,East,,fourth one\n";
        AnalyzedTable t = analyze("sales.csv", csv);
        assertEquals(ColumnType.TEXT, t.columns().get(0).type());     // id-like name stays text
        assertEquals(ColumnType.DATE, t.columns().get(1).type());
        assertEquals(ColumnType.CATEGORY, t.columns().get(2).type());
        assertEquals(ColumnType.NUMBER, t.columns().get(3).type());
        assertEquals(1, t.columns().get(3).missing());
        assertEquals(1200.5, (Double) t.rows().get(0).get(3));
        assertEquals("2026-01-05", t.columns().get(1).minDate());
    }

    @Test
    void handlesSemicolonAndDayFirstDates() throws Exception {
        String csv = "Day;Sales\n25/12/2026;10\n01/01/2027;20\n13/02/2027;30\n";
        AnalyzedTable t = analyze("x.txt", csv);
        assertEquals(ColumnType.DATE, t.columns().get(0).type());
        assertEquals("2026-12-25", t.columns().get(0).minDate());
        assertEquals(ColumnType.NUMBER, t.columns().get(1).type());
    }

    @Test
    void buildsAllReportsOfBothKindsAndKeepsDataSafe() throws Exception {
        ReportService service = new ReportService(new ObjectMapper());
        String csv = "Name,Score\n\"</script><b>x</b>\",5\nAnna,7\nBob,9\n";
        AnalyzedTable generic = analyze("danger.csv", csv);
        AnalyzedTable cases = analyzer.analyze("cases.xlsx", parser.parse("cases.xlsx", testCaseWorkbook()));
        for (AnalyzedTable t : List.of(generic, cases)) {
            for (ReportService.Definition def : ReportService.definitionsFor(t)) {
                String html = service.generate(t, def);
                assertTrue(html.contains("const DATA = {"), "data embedded in " + def.template());
                assertFalse(html.contains("/*__DATA__*/"), "placeholder replaced in " + def.template());
                assertFalse(html.contains("/*__COMMON__*/"), "common script inserted in " + def.template());
                assertFalse(html.contains("/*__TC__*/"), "tc script inserted in " + def.template());
            }
        }
        String html = service.generate(generic, ReportService.definitionsFor(generic).get(0));
        assertTrue(html.contains("<" + "\\" + "/script>"), "script tag in data is escaped");
    }
}
