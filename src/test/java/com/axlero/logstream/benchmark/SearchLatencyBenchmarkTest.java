package com.axlero.logstream.benchmark;

import com.axlero.logstream.indexing.LogEvent;
import com.axlero.logstream.indexing.LogSearchCriteria;
import com.axlero.logstream.indexing.LuceneIndexService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Day 6: Search Latency Benchmark — verifies <50ms p95 on warmed-up Lucene index.
 * Uses in-memory Lucene index (ByteBuffersDirectory) for reproducible CI benchmarks.
 * JVM warmup rounds (5) are performed before recording latency to ensure JIT compilation.
 */
class SearchLatencyBenchmarkTest {

    private LuceneIndexService indexService;
    private static final int INDEX_SIZE    = 100_000; // 100k docs — fast to index in-memory
    private static final int WARMUP_ROUNDS = 5;       // JIT warmup before measurement
    private static final int ITERATIONS    = 30;      // measurement iterations
    private static final long P95_LIMIT_MS = 50;      // SLA target

    private static final String[] SERVICES = {"billing-api", "auth-service", "order-service", "search-api", "payment-gw"};
    private static final String[] LEVELS   = {"INFO", "INFO", "WARN", "ERROR", "DEBUG"};
    private static final String[] MSGS     = {
        "Request completed", "Payment DB timeout", "Connection refused",
        "User login success", "Cache miss", "Slow query detected", "Database error"
    };

    @BeforeEach
    void setUp() throws IOException {
        indexService = new LuceneIndexService();
        indexLogs(INDEX_SIZE);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (indexService != null) indexService.close();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void indexLogs(int count) throws IOException {
        Random r = new Random(42);
        long end   = Instant.now().toEpochMilli();
        long start = end - 3_600_000L;
        for (int i = 0; i < count; i++) {
            indexService.index(new LogEvent(
                Instant.ofEpochMilli(start + (long)(r.nextDouble() * (end - start))),
                LEVELS[r.nextInt(LEVELS.length)],
                SERVICES[r.nextInt(SERVICES.length)],
                MSGS[r.nextInt(MSGS.length)] + " id=" + i
            ));
        }
    }

    private long measureP95(SearchLatencyBenchmarkTest.SearchAction action) throws Exception {
        // JVM warmup — results discarded
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            action.run();
        }
        // Actual measurement
        List<Long> latencies = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            long t = System.nanoTime();
            action.run();
            latencies.add((System.nanoTime() - t) / 1_000_000L);
        }
        Collections.sort(latencies);
        int p50idx = (int)(ITERATIONS * 0.50);
        int p95idx = (int)(ITERATIONS * 0.95);
        long p50 = latencies.get(p50idx);
        long p95 = latencies.get(p95idx);
        System.out.printf("  p50=%dms  p95=%dms  min=%dms  max=%dms (n=%d, warmup=%d)%n",
            p50, p95, latencies.get(0), latencies.get(ITERATIONS - 1), ITERATIONS, WARMUP_ROUNDS);
        return p95;
    }

    @FunctionalInterface
    interface SearchAction {
        void run() throws Exception;
    }

    // ── tests ──────────────────────────────────────────────────────────────────

    @Test
    void termSearchP95Under50ms() throws Exception {
        System.out.println("Benchmark [term='timeout']:");
        long p95 = measureP95(() ->
            indexService.search(new LogSearchCriteria("timeout", null, null, null, null, 50)));
        assertTrue(p95 <= P95_LIMIT_MS,
            "p95 search latency " + p95 + "ms exceeds " + P95_LIMIT_MS + "ms target");
    }

    @Test
    void levelFilterSearchP95Under50ms() throws Exception {
        System.out.println("Benchmark [level=ERROR]:");
        long p95 = measureP95(() ->
            indexService.search(new LogSearchCriteria(null, "ERROR", null, null, null, 100)));
        assertTrue(p95 <= P95_LIMIT_MS,
            "p95 level-filter latency " + p95 + "ms exceeds " + P95_LIMIT_MS + "ms target");
    }

    @Test
    void serviceFilterSearchP95Under50ms() throws Exception {
        System.out.println("Benchmark [service=auth-service]:");
        long p95 = measureP95(() ->
            indexService.search(new LogSearchCriteria(null, null, "auth-service", null, null, 100)));
        assertTrue(p95 <= P95_LIMIT_MS,
            "p95 service-filter latency " + p95 + "ms exceeds " + P95_LIMIT_MS + "ms target");
    }

    @Test
    void combinedFilterSearchP95Under50ms() throws Exception {
        System.out.println("Benchmark [combined: term=database level=ERROR service=payment-gw]:");
        long p95 = measureP95(() ->
            indexService.search(new LogSearchCriteria("database", "ERROR", "payment-gw", null, null, 50)));
        assertTrue(p95 <= P95_LIMIT_MS,
            "p95 combined-filter latency " + p95 + "ms exceeds " + P95_LIMIT_MS + "ms target");
    }
}
