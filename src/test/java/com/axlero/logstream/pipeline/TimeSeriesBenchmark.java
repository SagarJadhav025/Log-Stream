
package com.axlero.logstream.pipeline;

import com.axlero.logstream.api.TimeSeriesService;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TimeSeriesBenchmark {

    private LogIndexer indexer;
    private TimeSeriesService service;
    private Path indexPath;

    private Query matchAllQuery;
    private Query errorQuery;
    private Query serviceQuery;
    private Query textQuery;
    private Query combinedQuery;

    private long from;
    private long to;

    @Setup(Level.Trial)
    public void setUp() throws Exception {

        indexPath = Files.createTempDirectory("logstream-benchmark-");

        indexer = new LogIndexer();

        org.springframework.test.util.ReflectionTestUtils.setField(
                indexer,
                "indexDir",
                indexPath.toString()
        );

        indexer.init();

        service = new TimeSeriesService(indexer);

        long baseTime = System.currentTimeMillis();

        // Half-open interval: [from, to)
        from = baseTime - 60_000L;
        to = baseTime;

        // Index exactly 10,000 documents inside the time range.
        for (int i = 0; i < 10_000; i++) {

            String level = i % 5 == 0 ? "ERROR" : "INFO";

            String serviceName = i % 2 == 0
                    ? "billing-api"
                    : "payments-api";

            String message = i % 10 == 0
                    ? "Database timeout occurred request-" + i
                    : "Request processed successfully request-" + i;

            indexer.index(new LogEntry(
                    baseTime - 1L - (i % 60_000L),
                    level,
                    serviceName,
                    50L + (i % 950),
                    message
            ));
        }

        matchAllQuery = new MatchAllDocsQuery();

        errorQuery = new TermQuery(
                new Term("level", "ERROR")
        );

        serviceQuery = new TermQuery(
                new Term("service", "billing-api")
        );

        textQuery = new QueryParser(
                "message",
                new StandardAnalyzer()
        ).parse("timeout");

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        builder.add(errorQuery, BooleanClause.Occur.MUST);
        builder.add(serviceQuery, BooleanClause.Occur.MUST);
        builder.add(textQuery, BooleanClause.Occur.MUST);

        combinedQuery = builder.build();

        indexer.commit();
    }

    @Benchmark
    public Object histogramMatchAll() throws Exception {
        return service.histogram(from, to, 5_000L, matchAllQuery);
    }

    @Benchmark
    public Object histogramErrorFilter() throws Exception {
        return service.histogram(from, to, 5_000L, errorQuery);
    }

    @Benchmark
    public Object histogramServiceFilter() throws Exception {
        return service.histogram(from, to, 5_000L, serviceQuery);
    }

    @Benchmark
    public Object histogramTextFilter() throws Exception {
        return service.histogram(from, to, 5_000L, textQuery);
    }

    @Benchmark
    public Object histogramCombinedFilter() throws Exception {
        return service.histogram(from, to, 5_000L, combinedQuery);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {

        if (indexer != null) {
            indexer.close();
        }

        if (indexPath != null) {
            try (var paths = Files.walk(indexPath)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (java.io.IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        });
            }
        }
    }
}
