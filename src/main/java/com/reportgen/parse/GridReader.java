package com.reportgen.parse;

import com.reportgen.model.RawTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a raw grid of cells (from Excel or CSV) into a clean table.
 *
 * Real-world sheets are rarely a plain table. They have a title block on top, several tables one below
 * another with their own header rows, section titles in between, and many empty rows and columns.
 * This reader:
 *  - finds the real header row(s) instead of trusting row 1,
 *  - removes empty rows and empty columns, so there are no "Column 5, Column 6",
 *  - merges tables that repeat the header (even when some tables have fewer columns),
 *  - keeps section titles in a "Section" column, and
 *  - keeps loose facts (Ticket, Screen Name, Date, ...) as meta information.
 */
public final class GridReader {

    private GridReader() {
    }

    /** IDs like TC-UP-01, REQ_12, BUG-7. A short row starting with one of these is data, not a title. */
    private static final Pattern ID_LIKE = Pattern.compile("^[A-Za-z]{1,12}[-_][A-Za-z0-9_-]*\\d[A-Za-z0-9_-]*$");
    private static final Pattern TICKET_KEY = Pattern.compile("(?i)^(ticket|jira( id)?|issue|story)$");
    private static final Pattern TITLE_PREFIX = Pattern.compile("(?i)^test cases?\\s*[:\\-–]\\s*");

    /** One table found in the sheet. */
    private static final class Block {
        final String[] header;
        final List<String[]> rows = new ArrayList<>();
        final String section;
        final String group;

        Block(String[] header, String section, String group) {
            this.header = header;
            this.section = section;
            this.group = group;
        }
    }

    public static RawTable read(List<List<String>> grid, String sheetName, int maxRows) {
        // ---- normalise ----
        List<String[]> rows = new ArrayList<>(grid.size());
        int width = 0;
        for (List<String> g : grid) {
            String[] a = new String[g.size()];
            for (int c = 0; c < a.length; c++) {
                a[c] = norm(g.get(c));
                if (!a[c].isEmpty()) {
                    width = Math.max(width, c + 1);
                }
            }
            rows.add(a);
        }
        if (width == 0) {
            throw new IllegalArgumentException("The file is empty.");
        }
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            String[] a = rows.get(i);
            if (a.length < width) {
                String[] p = new String[width];
                for (int c = 0; c < width; c++) {
                    p[c] = c < a.length ? a[c] : "";
                }
                rows.set(i, p);
            }
        }
        int[] ne = new int[n];
        int maxNe = 0;
        for (int i = 0; i < n; i++) {
            for (String v : rows.get(i)) {
                if (!v.isEmpty()) {
                    ne[i]++;
                }
            }
            maxNe = Math.max(maxNe, ne[i]);
        }
        int minHeader = Math.min(3, Math.max(maxNe, 1));

        // ---- find header rows ----
        List<Integer> headerRows = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (ne[i] < minHeader || !allTextual(rows.get(i)) || !hasDataAfter(ne, i)) {
                continue;
            }
            if (headerRows.isEmpty()) {
                headerRows.add(i);
                addLower(known, rows.get(i));
            } else {
                int overlap = 0;
                for (String v : rows.get(i)) {
                    if (!v.isEmpty() && known.contains(v.toLowerCase(Locale.ROOT))) {
                        overlap++;
                    }
                }
                if (overlap >= 2 || (overlap >= 1 && overlap >= ne[i] * 0.6)) {
                    headerRows.add(i);
                    addLower(known, rows.get(i));
                }
            }
        }
        if (headerRows.isEmpty()) {
            for (int i = 0; i < n; i++) {
                if (ne[i] > 0) {
                    headerRows.add(i);
                    break;
                }
            }
        }
        Set<Integer> headerSet = new HashSet<>(headerRows);
        boolean sectioned = headerRows.size() >= 2;

        // ---- walk the sheet ----
        Map<String, List<String>> meta = new LinkedHashMap<>();
        List<Block> blocks = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        String section = null;
        String group = null;
        Block cur = null;
        for (int i = 0; i < n; i++) {
            if (ne[i] == 0) {
                continue;
            }
            String[] a = rows.get(i);
            if (headerSet.contains(i)) {
                if (!pending.isEmpty()) {
                    section = pending.get(pending.size() - 1);
                    if (pending.size() > 1) {
                        group = String.join(" › ", pending.subList(0, pending.size() - 1));
                    }
                    pending.clear();
                }
                cur = new Block(a.clone(), section, group);
                blocks.add(cur);
            } else if (cur == null || (sectioned && isContext(a, ne[i]))) {
                context(a, meta, pending);
            } else {
                cur.rows.add(a);
            }
        }
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Could not find a table of data in this file.");
        }

        // ---- resolve column names per block, then merge by name ----
        Map<String, Integer> keyToIndex = new LinkedHashMap<>();
        List<String> display = new ArrayList<>();
        List<String[]> prevNames = new ArrayList<>();
        List<String[]> namesPerBlock = new ArrayList<>();
        String[] previous = null;
        for (Block b : blocks) {
            String[] names = resolveNames(b, width, previous);
            namesPerBlock.add(names);
            previous = names;
            for (String nm : names) {
                if (nm != null && !keyToIndex.containsKey(key(nm))) {
                    keyToIndex.put(key(nm), display.size());
                    display.add(nm);
                }
            }
        }

        List<List<String>> merged = new ArrayList<>();
        List<String> sectionLabels = new ArrayList<>();
        for (int bi = 0; bi < blocks.size(); bi++) {
            Block b = blocks.get(bi);
            String[] names = namesPerBlock.get(bi);
            String label = b.section == null ? null : (b.group == null ? b.section : b.group + " › " + b.section);
            for (String[] r : b.rows) {
                List<String> out = new ArrayList<>(java.util.Collections.nCopies(display.size(), (String) null));
                boolean any = false;
                for (int c = 0; c < width; c++) {
                    if (names[c] != null && !r[c].isEmpty()) {
                        out.set(keyToIndex.get(key(names[c])), r[c]);
                        any = true;
                    }
                }
                if (any) {
                    merged.add(out);
                    sectionLabels.add(label);
                }
            }
        }
        if (merged.isEmpty()) {
            throw new IllegalArgumentException("The file has a header row but no data rows.");
        }

        // ---- drop columns that have no data at all ----
        List<Integer> keep = new ArrayList<>();
        for (int c = 0; c < display.size(); c++) {
            for (List<String> r : merged) {
                if (r.get(c) != null) {
                    keep.add(c);
                    break;
                }
            }
        }
        List<String> headers = new ArrayList<>();
        for (int c : keep) {
            headers.add(display.get(c));
        }
        List<List<String>> finalRows = new ArrayList<>(merged.size());
        for (List<String> r : merged) {
            List<String> out = new ArrayList<>(headers.size() + 1);
            for (int c : keep) {
                out.add(r.get(c));
            }
            finalRows.add(out);
        }

        // ---- add a Section column when the sheet has several sections ----
        String sectionColumn = null;
        Set<String> distinctSections = new HashSet<>();
        for (String s : sectionLabels) {
            if (s != null) {
                distinctSections.add(s);
            }
        }
        if (distinctSections.size() >= 2 && headers.stream().noneMatch(h -> h.equalsIgnoreCase("Section"))) {
            sectionColumn = "Section";
            headers.add(sectionColumn);
            for (int r = 0; r < finalRows.size(); r++) {
                finalRows.get(r).add(sectionLabels.get(r));
            }
        }

        int total = finalRows.size();
        boolean truncated = total > maxRows;
        if (truncated) {
            finalRows = new ArrayList<>(finalRows.subList(0, maxRows));
        }
        return new RawTable(headers, finalRows, meta, sheetName, sectionColumn, truncated, total);
    }

    // ---------- helpers ----------

    private static String[] resolveNames(Block b, int width, String[] previous) {
        String[] names = new String[width];
        Set<String> used = new HashSet<>();
        for (int c = 0; c < width; c++) {
            String h = b.header[c];
            if (!h.isEmpty()) {
                String unique = h;
                int k = 2;
                while (!used.add(key(unique))) {
                    unique = h + " (" + k++ + ")";
                }
                names[c] = unique;
            }
        }
        for (int c = 0; c < width; c++) {
            if (names[c] != null) {
                continue;
            }
            int filled = 0;
            int statusLike = 0;
            for (String[] r : b.rows) {
                if (!r[c].isEmpty()) {
                    filled++;
                    if (StatusVocabulary.isStatusLike(r[c])) {
                        statusLike++;
                    }
                }
            }
            if (filled == 0) {
                continue; // nothing in this column for this table
            }
            if (statusLike >= filled * 0.5 && !used.contains("status")) {
                names[c] = "Status";
            } else if (previous != null && previous[c] != null && !used.contains(key(previous[c]))) {
                names[c] = previous[c];
            } else {
                names[c] = "Column " + (c + 1);
            }
            used.add(key(names[c]));
        }
        return names;
    }

    /** A short row that sits between tables: a title, a date, or a "Ticket | link" pair. */
    private static boolean isContext(String[] a, int count) {
        if (count > 2) {
            return false;
        }
        for (String v : a) {
            if (!v.isEmpty()) {
                return !ID_LIKE.matcher(v).matches();
            }
        }
        return false;
    }

    private static void context(String[] a, Map<String, List<String>> meta, List<String> pending) {
        List<String> cells = new ArrayList<>();
        for (String v : a) {
            if (!v.isEmpty()) {
                cells.add(v);
            }
        }
        if (cells.size() == 1) {
            String v = cells.get(0);
            if (ValueParsers.looksLikeDate(v)) {
                addMeta(meta, "Date", v);
            } else {
                pending.add(cleanTitle(v));
            }
        } else if (cells.size() == 2) {
            addMeta(meta, metaKey(cells.get(0)), cells.get(1));
        } else if (cells.size() > 2) {
            addMeta(meta, metaKey(cells.get(0)), String.join(" · ", cells.subList(1, cells.size())));
        }
    }

    private static String metaKey(String k) {
        String key = k.replaceAll("[:\\s]+$", "").trim();
        return TICKET_KEY.matcher(key).matches() ? "Ticket" : key;
    }

    private static void addMeta(Map<String, List<String>> meta, String key, String value) {
        List<String> list = meta.computeIfAbsent(key, k -> new ArrayList<>());
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static String cleanTitle(String v) {
        String t = TITLE_PREFIX.matcher(v).replaceFirst("");
        return t.replaceAll("\\s*[\\r\\n]+\\s*", " – ").trim();
    }

    private static boolean allTextual(String[] a) {
        for (String v : a) {
            if (!v.isEmpty() && (ValueParsers.parseNumber(v) != null || v.length() > 100)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDataAfter(int[] ne, int i) {
        for (int j = i + 1; j < ne.length; j++) {
            if (ne[j] > 0) {
                return true;
            }
        }
        return false;
    }

    private static void addLower(Set<String> set, String[] a) {
        for (String v : a) {
            if (!v.isEmpty()) {
                set.add(v.toLowerCase(Locale.ROOT));
            }
        }
    }

    private static String key(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String norm(String s) {
        return s == null ? "" : s.replace(' ', ' ').trim();
    }
}
