package com.reportgen.model;

/** Column positions of a test-case sheet. -1 means the sheet has no such column. */
public record TestCaseInfo(int id, int title, int precondition, int steps, int expected, int status, int section) {
}
