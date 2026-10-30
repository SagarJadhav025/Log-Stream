package com.axlero.logstream.pipeline;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "logstream.seed", havingValue = "true")
public class MockLogGenerator implements CommandLineRunner {
    private static final int TOTAL = 1_000_000;
    private static final String[] LEVELS = {"INFO", "INFO", "INFO", "WARN", "ERROR", "DEBUG"};
    private static final String[] SERVICES = {"billing-api", "auth-service", "order-service", "search-api", "payment-gw"};
    private static final String[] MSGS = {"Request completed", "Payment DB timeout",
            "Connection refused", "User login success", "Cache miss", "Slow query detected"};

    private final LogIndexer indexer;

    public MockLogGenerator(LogIndexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public void run(String... args) throws Exception {
        Random r = new Random(42);
        long end = Instant.now().toEpochMilli();
        long start = end - 3_600_000L;

        long t0 = System.nanoTime();
        for (int i = 0; i < TOTAL; i++) {
            indexer.index(new LogEntry(
                    start + (long) (r.nextDouble() * (end - start)),
                    LEVELS[r.nextInt(LEVELS.length)],
                    SERVICES[r.nextInt(SERVICES.length)],
                    50 + r.nextInt(2500),
                    MSGS[r.nextInt(MSGS.length)] + " id=" + i));
        }
        indexer.commit();
        double sec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("INDEXED %d logs in %.1fs (%.0f logs/sec)%n", TOTAL, sec, TOTAL / sec);

        try (DirectoryReader reader = DirectoryReader.open(indexer.writer())) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query q = new QueryParser("message", new StandardAnalyzer()).parse("timeout");
            for (int i = 1; i <= 5; i++) {
                long t = System.nanoTime();
                TopDocs hits = searcher.search(q, 50);
                System.out.printf("SEARCH run %d: hits=%d took=%.2fms%n",
                        i, hits.totalHits.value(), (System.nanoTime() - t) / 1e6);
            }
        }
    }
}
