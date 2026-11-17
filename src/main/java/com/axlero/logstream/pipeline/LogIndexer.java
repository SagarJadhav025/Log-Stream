package com.axlero.logstream.pipeline;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class LogIndexer {

    private static final Logger logger = LoggerFactory.getLogger(LogIndexer.class);

    @Value("${logstream.index-dir:./lucene-index}")
    private String indexDir;

    @Value("${logstream.lucene.ram-buffer-mb:256}")
    private double ramBufferMb = 256.0;

    @Value("${logstream.lucene.refresh-interval-ms:1000}")
    private long refreshIntervalMs = 1000;

    @Value("${logstream.lucene.commit-interval-seconds:30}")
    private long commitIntervalSeconds = 30;

    @Value("${logstream.lucene.max-merged-segment-mb:512}")
    private double maxMergedSegmentMb = 512.0;

    @Value("${logstream.lucene.segments-per-tier:10}")
    private double segmentsPerTier = 10.0;

    private FSDirectory directory;
    private IndexWriter writer;
    private SearcherManager searcherManager;
    private ScheduledExecutorService nrtScheduler;

    public LogIndexer() {
    }

    public LogIndexer(String indexDir) {
        this.indexDir = indexDir;
    }

    @PostConstruct
    public void init() throws IOException {
        String dir = (indexDir != null && !indexDir.isBlank()) ? indexDir : "./lucene-index";
        directory = FSDirectory.open(Path.of(dir));

        // Day 2: TieredMergePolicy tuning for high-throughput batching & segment consolidation
        TieredMergePolicy mergePolicy = new TieredMergePolicy();
        mergePolicy.setMaxMergedSegmentMB(maxMergedSegmentMb);
        mergePolicy.setSegmentsPerTier(segmentsPerTier);
        mergePolicy.setMaxMergeAtOnce((int) Math.max(2, segmentsPerTier));

        IndexWriterConfig cfg = new IndexWriterConfig(new StandardAnalyzer());
        cfg.setRAMBufferSizeMB(ramBufferMb);
        cfg.setMergePolicy(mergePolicy);
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        writer = new IndexWriter(directory, cfg);

        // Day 2: Near Real-Time (NRT) SearcherManager over IndexWriter
        searcherManager = new SearcherManager(writer, new SearcherFactory());

        // Background scheduler for periodic NRT soft refresh & hard disk commits
        nrtScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "lucene-nrt-scheduler");
            t.setDaemon(true);
            return t;
        });

        // 1. Periodic soft refresh (NRT search visibility without disk fsync)
        if (refreshIntervalMs > 0) {
            nrtScheduler.scheduleWithFixedDelay(() -> {
                try {
                    maybeRefresh();
                } catch (Exception e) {
                    logger.warn("Error during Lucene NRT maybeRefresh: {}", e.getMessage());
                }
            }, refreshIntervalMs, refreshIntervalMs, TimeUnit.MILLISECONDS);
        }

        // 2. Periodic asynchronous hard disk commit
        if (commitIntervalSeconds > 0) {
            nrtScheduler.scheduleWithFixedDelay(() -> {
                try {
                    commit();
                } catch (Exception e) {
                    logger.error("Error during scheduled Lucene disk commit: {}", e.getMessage());
                }
            }, commitIntervalSeconds, commitIntervalSeconds, TimeUnit.SECONDS);
        }

        logger.info("LogIndexer initialized: dir={}, ramBuffer={}MB, maxMergedSegment={}MB, refresh={}ms, commit={}s",
                dir, ramBufferMb, maxMergedSegmentMb, refreshIntervalMs, commitIntervalSeconds);
    }

    public void index(LogEntry e) throws IOException {
        Document d = toDocument(e);
        writer.addDocument(d);
    }

    public void indexBatch(List<LogEntry> entries) throws IOException {
        if (writer == null || entries == null || entries.isEmpty()) {
            return;
        }
        List<Document> docs = new ArrayList<>(entries.size());
        for (LogEntry e : entries) {
            docs.add(toDocument(e));
        }
        writer.addDocuments(docs);
    }

    private Document toDocument(LogEntry e) {
        Document d = new Document();
        d.add(new StringField("level", e.level() != null ? e.level() : "INFO", Field.Store.YES));
        d.add(new StringField("service", e.service() != null ? e.service() : "unknown", Field.Store.YES));
        d.add(new TextField("message", e.message() != null ? e.message() : "", Field.Store.YES));
        d.add(new LongPoint("timestamp", e.timestamp()));
        d.add(new NumericDocValuesField("timestamp", e.timestamp()));
        d.add(new StoredField("timestamp_stored", e.timestamp()));
        d.add(new LongPoint("response_time", e.responseTime()));
        d.add(new NumericDocValuesField("response_time", e.responseTime()));
        d.add(new StoredField("response_time_stored", e.responseTime()));
        return d;
    }

    /**
     * Acquires an active NRT IndexSearcher. Automatically performs a fast maybeRefresh()
     * so uncommitted in-memory index changes are visible in near real-time.
     * Caller must release it via {@link #releaseSearcher(IndexSearcher)}.
     */
    public IndexSearcher acquireSearcher() throws IOException {
        if (searcherManager == null) {
            throw new IllegalStateException("SearcherManager is not initialized");
        }
        searcherManager.maybeRefresh();
        return searcherManager.acquire();
    }

    /**
     * Releases an acquired IndexSearcher back to the NRT pool.
     */
    public void releaseSearcher(IndexSearcher searcher) {
        if (searcherManager != null && searcher != null) {
            try {
                searcherManager.release(searcher);
            } catch (IOException e) {
                logger.warn("Failed to release Lucene IndexSearcher: {}", e.getMessage());
            }
        }
    }

    /**
     * Soft refreshes the SearcherManager to make uncommitted in-memory index changes searchable immediately.
     */
    public boolean maybeRefresh() throws IOException {
        if (searcherManager != null) {
            return searcherManager.maybeRefresh();
        }
        return false;
    }

    public void commit() throws IOException {
        if (writer != null) {
            writer.commit();
        }
        if (searcherManager != null) {
            searcherManager.maybeRefresh();
        }
    }

    public IndexWriter writer() {
        return writer;
    }

    public SearcherManager searcherManager() {
        return searcherManager;
    }

    @PreDestroy
    public void close() throws IOException {
        if (nrtScheduler != null) {
            nrtScheduler.shutdown();
            try {
                if (!nrtScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    nrtScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                nrtScheduler.shutdownNow();
            }
        }
        if (searcherManager != null) {
            searcherManager.close();
        }
        if (writer != null) {
            writer.commit();
            writer.close();
        }
        if (directory != null) {
            directory.close();
        }
    }
}
