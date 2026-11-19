package com.reportgen.parse;

import java.util.Locale;
import java.util.Set;

/** Words that mean "result of a test / state of a task". Used to find Status columns. */
public final class StatusVocabulary {

    /** Results of a test run. */
    private static final Set<String> TEST_RESULTS = Set.of(
            "pass", "passed", "fail", "failed", "failure", "blocked", "block",
            "skip", "skipped", "not run", "not executed", "not tested", "untested",
            "n/a", "na", "not applicable", "no run", "in progress", "inprogress", "pending");

    /** Looser list, used only to name a column that has no header. */
    private static final Set<String> LOOSE = Set.of("ok", "success", "error", "open", "closed", "done", "todo", "to do");

    private StatusVocabulary() {
    }

    public static boolean isTestResult(String v) {
        return v != null && TEST_RESULTS.contains(v.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isStatusLike(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return TEST_RESULTS.contains(s) || LOOSE.contains(s);
    }
}
