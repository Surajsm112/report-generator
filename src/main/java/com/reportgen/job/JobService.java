package com.reportgen.job;

import com.reportgen.analysis.TableAnalyzer;
import com.reportgen.model.AnalyzedTable;
import com.reportgen.model.RawTable;
import com.reportgen.parse.FileParser;
import com.reportgen.report.ReportService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs uploads in the background so the website never freezes.
 * - ExecutorService: a small pool of worker threads takes uploads one after another.
 * - CompletableFuture: the three reports of one upload are built in parallel.
 * - ConcurrentHashMap: jobs are shared between worker threads and HTTP request threads.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final Duration KEEP_FOR = Duration.ofHours(1);

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final ExecutorService reportPool = Executors.newFixedThreadPool(3);

    private final FileParser parser = new FileParser();
    private final TableAnalyzer analyzer = new TableAnalyzer();
    private final ReportService reportService;

    public JobService(ReportService reportService) {
        this.reportService = reportService;
    }

    public Job submit(String fileName, byte[] content) {
        purgeOldJobs();
        Job job = new Job(UUID.randomUUID().toString(), fileName);
        jobs.put(job.getId(), job);
        workers.submit(() -> process(job, content));
        return job;
    }

    public Job get(String id) {
        return jobs.get(id);
    }

    private void process(Job job, byte[] content) {
        try {
            job.update(Job.Status.PARSING, 10, "Reading your file…");
            RawTable raw = parser.parse(job.getFileName(), content);

            job.update(Job.Status.ANALYZING, 30, "Detecting column types (numbers, dates, categories)…");
            AnalyzedTable table = analyzer.analyze(job.getFileName(), raw);
            job.setDataInfo(table.rows().size(), table.columns().size(), table.truncated(), table.totalRows());
            List<ReportService.Definition> plan = ReportService.definitionsFor(table);
            job.setReportPlan(table.testCase() != null ? "testcases" : "data", plan);

            job.update(Job.Status.GENERATING, 50, "Generating your three reports…");
            AtomicInteger finished = new AtomicInteger();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (ReportService.Definition def : plan) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        job.getReports().put(def.no(), reportService.generate(table, def));
                    } catch (Exception e) {
                        throw new IllegalStateException("Could not build report " + def.no(), e);
                    }
                    int done = finished.incrementAndGet();
                    job.setProgress(50 + done * 16, "Built report " + done + " of 3: " + def.name());
                }, reportPool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            job.update(Job.Status.DONE, 100, "Your reports are ready");
        } catch (Exception e) {
            log.warn("Job {} failed", job.getId(), e);
            Throwable cause = e.getCause() != null && e instanceof java.util.concurrent.CompletionException
                    ? e.getCause() : e;
            job.update(Job.Status.FAILED, 100, friendly(cause));
        }
    }

    private String friendly(Throwable t) {
        if (t instanceof IllegalArgumentException) {
            return t.getMessage();
        }
        return "We could not read this file. Check that it is a valid Excel, CSV or text file with a header row.";
    }

    /** Runs every 10 minutes so uploads are really gone after an hour, even if nobody uploads again. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    void purgeOldJobs() {
        Instant cutoff = Instant.now().minus(KEEP_FOR);
        jobs.values().removeIf(j -> j.getCreatedAt().isBefore(cutoff));
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
        reportPool.shutdownNow();
    }
}
