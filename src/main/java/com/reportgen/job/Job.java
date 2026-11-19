package com.reportgen.job;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One upload being processed. Fields are volatile because a worker thread writes and HTTP threads read. */
public class Job {

    public enum Status { QUEUED, PARSING, ANALYZING, GENERATING, DONE, FAILED }

    private final String id;
    private final String fileName;
    private final Instant createdAt = Instant.now();

    private volatile Status status = Status.QUEUED;
    private volatile int progress = 0;
    private volatile String message = "Waiting to start";
    private volatile int rows;
    private volatile int columns;
    private volatile boolean truncated;
    private volatile int totalRows;
    private volatile java.util.List<com.reportgen.report.ReportService.Definition> definitions = java.util.List.of();
    private volatile String kind = "data";

    /** report number -> generated HTML. ConcurrentHashMap: three threads put results at the same time. */
    private final Map<Integer, String> reports = new ConcurrentHashMap<>();

    public Job(String id, String fileName) {
        this.id = id;
        this.fileName = fileName;
    }

    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public int getProgress() { return progress; }
    public String getMessage() { return message; }
    public int getRows() { return rows; }
    public int getColumns() { return columns; }
    public boolean isTruncated() { return truncated; }
    public int getTotalRows() { return totalRows; }
    public Map<Integer, String> getReports() { return reports; }
    public java.util.List<com.reportgen.report.ReportService.Definition> getDefinitions() { return definitions; }
    /** "testcases" or "data": tells the website what kind of file it recognised. */
    public String getKind() { return kind; }

    public void setReportPlan(String kind, java.util.List<com.reportgen.report.ReportService.Definition> definitions) {
        this.kind = kind;
        this.definitions = definitions;
    }

    public com.reportgen.report.ReportService.Definition definition(int no) {
        return definitions.stream().filter(d -> d.no() == no).findFirst().orElse(null);
    }

    public void update(Status status, int progress, String message) {
        this.status = status;
        this.progress = progress;
        this.message = message;
    }

    public void setProgress(int progress, String message) {
        this.progress = progress;
        this.message = message;
    }

    public void setDataInfo(int rows, int columns, boolean truncated, int totalRows) {
        this.rows = rows;
        this.columns = columns;
        this.truncated = truncated;
        this.totalRows = totalRows;
    }
}
