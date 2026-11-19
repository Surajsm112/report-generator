package com.reportgen.web;

import com.reportgen.job.Job;
import com.reportgen.job.JobService;
import com.reportgen.report.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class ReportController {

    private static final List<String> ALLOWED = List.of(".csv", ".tsv", ".txt", ".xlsx", ".xls");

    private final JobService jobs;

    public ReportController(JobService jobs) {
        this.jobs = jobs;
    }

    /** Step 1: upload. Returns immediately with a job id; the work continues in the background. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "data" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty.");
        }
        if (ALLOWED.stream().noneMatch(lower::endsWith)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please upload an Excel (.xlsx, .xls), CSV, TSV or text (.txt) file.");
        }
        Job job = jobs.submit(name, file.getBytes());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", job.getId()));
    }

    /** Step 2: the website asks this every second to show progress. */
    @GetMapping("/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        Job job = find(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", job.getStatus());
        body.put("progress", job.getProgress());
        body.put("message", job.getMessage());
        body.put("fileName", job.getFileName());
        if (job.getStatus() == Job.Status.DONE) {
            body.put("rows", job.getRows());
            body.put("columns", job.getColumns());
            body.put("truncated", job.isTruncated());
            body.put("totalRows", job.getTotalRows());
            body.put("kind", job.getKind());
            List<Map<String, Object>> reports = new ArrayList<>();
            for (ReportService.Definition d : job.getDefinitions()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("no", d.no());
                r.put("name", d.name());
                r.put("description", d.description());
                reports.add(r);
            }
            body.put("reports", reports);
        }
        return body;
    }

    /** Step 3a: preview inside the page (shown in an iframe). */
    @GetMapping(value = "/{id}/reports/{no}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@PathVariable String id, @PathVariable int no) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html(id, no));
    }

    /** Step 3b: download the same report as a file. */
    @GetMapping(value = "/{id}/reports/{no}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, @PathVariable int no) {
        Job job = find(id);
        String html = html(id, no);
        String base = job.getFileName().replaceFirst("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9._-]", "_");
        ReportService.Definition def = job.definition(no);
        String fileName = base + "-report" + no + "-" + (def == null ? "report" : def.name())
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + ".html";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html.getBytes(StandardCharsets.UTF_8));
    }

    private String html(String id, int no) {
        Job job = find(id);
        String html = job.getReports().get(no);
        if (html == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not ready");
        }
        return html;
    }

    private Job find(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or expired job");
        }
        return job;
    }
}
