package com.axlero.logstream.indexing;

import com.axlero.logstream.pipeline.LogEntry;
import com.axlero.logstream.pipeline.LogIndexer;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LuceneNrtSearcherManagerTest {

    @TempDir
    Path tempDir;

    private LogIndexer indexer;

    @BeforeEach
    void setUp() throws IOException {
        indexer = new LogIndexer();
        ReflectionTestUtils.setField(indexer, "indexDir", tempDir.resolve("nrt-lucene").toString());
        ReflectionTestUtils.setField(indexer, "ramBufferMb", 64.0);
        ReflectionTestUtils.setField(indexer, "refreshIntervalMs", 50L);
        ReflectionTestUtils.setField(indexer, "commitIntervalSeconds", 60L);
        ReflectionTestUtils.setField(indexer, "maxMergedSegmentMb", 256.0);
        ReflectionTestUtils.setField(indexer, "segmentsPerTier", 5.0);
        indexer.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (indexer != null) {
            indexer.close();
        }
    }

    @Test
    void testNrtSearcherReflectsIndexedBatchWithoutHardCommit() throws IOException {
        assertNotNull(indexer.searcherManager(), "SearcherManager should be initialized");

        List<LogEntry> batch = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 50; i++) {
            batch.add(new LogEntry(
                    now + i,
                    i % 2 == 0 ? "ERROR" : "INFO",
                    "billing-service",
                    45,
                    "Transaction processing #" + i
            ));
        }

        indexer.indexBatch(batch);

        // Before soft refresh, raw NRT searcher has not refreshed
        IndexSearcher searcherBefore = indexer.searcherManager().acquire();
        try {
            Query query = new TermQuery(new Term("service", "billing-service"));
            TopDocs docsBefore = searcherBefore.search(query, 100);
            assertEquals(0, docsBefore.totalHits.value(), "Unrefreshed searcher should see 0 docs");
        } finally {
            indexer.searcherManager().release(searcherBefore);
        }

        // Trigger NRT soft refresh (Near Real-Time, NO disk commit)
        boolean refreshed = indexer.maybeRefresh();
        assertTrue(refreshed, "maybeRefresh should return true when new segments are available");

        // Now acquire refreshed searcher
        IndexSearcher searcherAfter = indexer.acquireSearcher();
        try {
            Query query = new TermQuery(new Term("service", "billing-service"));
            TopDocs docsAfter = searcherAfter.search(query, 100);
            assertEquals(50, docsAfter.totalHits.value(), "Refreshed NRT searcher should see all 50 docs");
        } finally {
            indexer.releaseSearcher(searcherAfter);
        }
    }

    @Test
    void testConcurrentSearcherAcquireAndRelease() throws Exception {
        // Populate index
        List<LogEntry> batch = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            batch.add(new LogEntry(now + i, "WARN", "gateway", 10, "Gateway latency #" + i));
        }
        indexer.indexBatch(batch);
        indexer.maybeRefresh();

        int threads = 10;
        int queriesPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int q = 0; q < queriesPerThread; q++) {
                        IndexSearcher searcher = indexer.acquireSearcher();
                        try {
                            Query query = new TermQuery(new Term("level", "WARN"));
                            TopDocs topDocs = searcher.search(query, 10);
                            if (topDocs.totalHits.value() == 100) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            indexer.releaseSearcher(searcher);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent reader threads should finish within timeout");
        assertEquals(threads * queriesPerThread, successCount.get(), "All concurrent queries should succeed");
    }
}
