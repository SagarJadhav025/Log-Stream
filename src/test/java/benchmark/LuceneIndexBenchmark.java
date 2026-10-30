
package com.axlero.logstream.benchmark;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.axlero.logstream.indexing.LogEvent;
import com.axlero.logstream.indexing.LuceneIndexService;
import com.axlero.logstream.indexing.LogSearchCriteria;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LuceneIndexBenchmark {

    private LuceneIndexService searchService;
    private LuceneIndexService indexingService;
    private int nextEventId;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        searchService = new LuceneIndexService();
        indexingService = new LuceneIndexService();

        for (int i = 0; i < 10_000; i++) {
            searchService.index(createEvent(i));
        }

        // Make the initial search index available to readers.
        searchService.search(new LogSearchCriteria(
                "database", null, null, null, null, 10
        ));
    }

    @Benchmark
    public void indexOneEvent() throws IOException {
        indexingService.index(createEvent(nextEventId++));
    }

    @Benchmark
    public Object searchExistingIndex() throws Exception {
        return searchService.search(new LogSearchCriteria(
                "database", "ERROR", "payment-service",
                null, null, 10
        ));
    }

    private LogEvent createEvent(int id) {
        String level = id % 5 == 0 ? "ERROR" : "INFO";
        String service = id % 2 == 0
                ? "payment-service"
                : "inventory-service";
        String message = id % 10 == 0
                ? "Database connection failed request-" + id
                : "Request processed successfully request-" + id;

        return new LogEvent(
                Instant.parse("2026-01-01T00:00:00Z")
                        .plusMillis(id),
                level,
                service,
                message
        );
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        try {
            if (searchService != null) {
                searchService.close();
            }
        } finally {
            if (indexingService != null) {
                indexingService.close();
            }
        }
    }
}
