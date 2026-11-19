package com.reportgen.parse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers that recognise numbers and dates in text cells. */
public final class ValueParsers {

    private ValueParsers() {
    }

    // ---------- numbers ----------

    private static final Pattern NUMBER = Pattern.compile("^[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?$");

    /** Parses "1,234.50", "₹ 1,200", "12%", "(45)" (negative). Returns null if not a number. */
    public static Double parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1).trim();
        }
        s = s.replaceAll("[₹$€£¥%\\s]", "").replace(",", "");
        if (!NUMBER.matcher(s).matches()) {
            return null;
        }
        try {
            double d = Double.parseDouble(s);
            return negative ? -d : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------- dates ----------

    /** Tried in this order; day-first is preferred over month-first for ambiguous values like 03/04/2026. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            fmt("uuuu-MM-dd"),
            fmt("uuuu/MM/dd"),
            fmt("dd/MM/uuuu"),
            fmt("dd-MM-uuuu"),
            fmt("dd.MM.uuuu"),
            fmt("MM/dd/uuuu"),
            fmt("d/M/uuuu"),
            fmt("d-M-uuuu"),
            fmt("M/d/uuuu"),
            fmt("dd-MMM-uuuu"),
            fmt("d-MMM-uuuu"),
            fmt("dd MMM uuuu"),
            fmt("d MMM uuuu"),
            fmt("MMM d, uuuu"),
            fmt("MMMM d, uuuu"),
            fmt("d MMMM uuuu"));

    private static final Pattern WITH_TIME = Pattern.compile("^(.*?)(?:[T\\s]+)\\d{1,2}:\\d{2}.*$");

    private static DateTimeFormatter fmt(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    }

    /** True if the text parses as a date in any of the supported formats. */
    public static boolean looksLikeDate(String s) {
        for (int f = 0; f < DATE_FORMATS.size(); f++) {
            if (parseDate(s, f) != null) {
                return true;
            }
        }
        return false;
    }

    public static int formatCount() {
        return DATE_FORMATS.size();
    }

    /** Parses with one specific format index (so a whole column uses the same format). */
    public static LocalDate parseDate(String raw, int formatIndex) {
        if (raw == null) {
            return null;
        }
        String s = stripTime(raw.trim());
        if (s.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(s, DATE_FORMATS.get(formatIndex));
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripTime(String s) {
        Matcher m = WITH_TIME.matcher(s);
        return m.matches() ? m.group(1).trim() : s;
    }
}
