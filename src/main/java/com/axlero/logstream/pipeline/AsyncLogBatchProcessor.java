package com.axlero.logstream.pipeline;

import com.axlero.logstream.streaming.LiveTailWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, non-blocking ingestion ring buffer & batch processor.
 * Decouples high-volume gRPC ingress threads from Lucene index I/O and WebSocket broadcasts.
 */
@Service
public class AsyncLogBatchProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AsyncLogBatchProcessor.class);

    @Value("${logstream.ingestion.buffer-capacity:100000}")
    private int bufferCapacity = 100000;

    @Value("${logstream.ingestion.batch-size:2000}")
    private int batchSize = 2000;

    @Value("${logstream.ingestion.flush-interval-ms:50}")
    private long flushIntervalMs = 50;

    @Value("${logstream.ingestion.websocket-sample-rate:10}")
    private int websocketSampleRate = 10;

    private final LogIndexer logIndexer;
    private final LiveTailWebSocketHandler liveTailWebSocketHandler;

    private BlockingQueue<LogEntry> bufferQueue;
    private ScheduledExecutorService flusherExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Metrics & Telemetry
    private final AtomicLong totalIngested = new AtomicLong(0);
    private final AtomicLong totalIndexed = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);
    private final AtomicLong batchesProcessed = new AtomicLong(0);

    public AsyncLogBatchProcessor(LogIndexer logIndexer, LiveTailWebSocketHandler liveTailWebSocketHandler) {
        this.logIndexer = logIndexer;
        this.liveTailWebSocketHandler = liveTailWebSocketHandler;
    }

    @PostConstruct
    public void start() {
        this.bufferQueue = new ArrayBlockingQueue<>(bufferCapacity);
        this.running.set(true);

        this.flusherExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "logstream-batch-flusher");
            t.setDaemon(true);
            return t;
        });

        this.flusherExecutor.scheduleWithFixedDelay(
                this::flushBatch,
                flushIntervalMs,
                flushIntervalMs,
                TimeUnit.MILLISECONDS
        );

        logger.info("AsyncLogBatchProcessor initialized: capacity={}, batchSize={}, flushIntervalMs={}ms, wsSampleRate={}",
                bufferCapacity, batchSize, flushIntervalMs, websocketSampleRate);
    }

    /**
     * Non-blocking ingestion into bounded buffer.
     * Called directly by gRPC worker threads.
     *
     * @param entry The parsed LogEntry
     * @return true if accepted into buffer, false if dropped due to buffer saturation
     */
    public boolean enqueue(LogEntry entry) {
        if (!running.get() || entry == null) {
            return false;
        }

        totalIngested.incrementAndGet();
        boolean accepted = bufferQueue.offer(entry);
        if (!accepted) {
            long drops = totalDropped.incrementAndGet();
            if (drops % 1000 == 1) {
                logger.warn("Ingestion buffer saturated! Dropped log entries so far: {}. Consider tuning buffer-capacity or batch-size.", drops);
            }
            return false;
        }

        // Fast-path eager flush trigger if buffer reaches batch size threshold
        if (bufferQueue.size() >= batchSize && flusherExecutor != null && !flusherExecutor.isShutdown()) {
            flusherExecutor.execute(this::flushBatch);
        }

        return true;
    }

    /**
     * Drains available logs in micro-batches and writes them in bulk to Lucene.
     */
    public synchronized void flushBatch() {
        if (bufferQueue == null || bufferQueue.isEmpty()) {
            return;
        }

        List<LogEntry> batch = new ArrayList<>(batchSize);
        bufferQueue.drainTo(batch, batchSize);

        if (batch.isEmpty()) {
            return;
        }

        try {
            // 1. Bulk index to Lucene
            if (logIndexer != null) {
                logIndexer.indexBatch(batch);
                totalIndexed.addAndGet(batch.size());
            }
            batchesProcessed.incrementAndGet();

            // 2. Broadcast sampled logs to WebSocket Live Tail without choking UI clients
            if (liveTailWebSocketHandler != null && websocketSampleRate > 0) {
                int count = 0;
                for (LogEntry log : batch) {
                    if (count++ % websocketSampleRate == 0) {
                        liveTailWebSocketHandler.broadcastLog(
                                log.timestamp(),
                                log.level(),
                                log.service(),
                                log.message()
                        );
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to index log batch of size {}: {}", batch.size(), e.getMessage(), e);
        }
    }

    @PreDestroy
    public void flushAndClose() {
        logger.info("Shutting down AsyncLogBatchProcessor... Draining remaining buffered logs.");
        running.set(false);

        if (flusherExecutor != null) {
            flusherExecutor.shutdown();
            try {
                if (!flusherExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    flusherExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                flusherExecutor.shutdownNow();
            }
        }

        // Drain any remaining entries
        if (bufferQueue != null && !bufferQueue.isEmpty()) {
            List<LogEntry> remaining = new ArrayList<>();
            bufferQueue.drainTo(remaining);
            try {
                if (!remaining.isEmpty() && logIndexer != null) {
                    logIndexer.indexBatch(remaining);
                    totalIndexed.addAndGet(remaining.size());
                    logIndexer.commit();
                }
            } catch (IOException e) {
                logger.error("Error writing remaining logs on shutdown: {}", e.getMessage(), e);
            }
        }

        logger.info("AsyncLogBatchProcessor shutdown complete. Ingested: {}, Indexed: {}, Dropped: {}, Batches: {}",
                totalIngested.get(), totalIndexed.get(), totalDropped.get(), batchesProcessed.get());
    }

    // Telemetry getters
    public long getTotalIngested() { return totalIngested.get(); }
    public long getTotalIndexed() { return totalIndexed.get(); }
    public long getTotalDropped() { return totalDropped.get(); }
    public long getBatchesProcessed() { return batchesProcessed.get(); }
    public int getQueueDepth() { return bufferQueue != null ? bufferQueue.size() : 0; }
    public int getBufferCapacity() { return bufferCapacity; }
    public boolean isRunning() { return running.get(); }
}
