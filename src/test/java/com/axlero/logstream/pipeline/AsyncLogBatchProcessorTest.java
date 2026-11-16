package com.axlero.logstream.pipeline;

import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import com.axlero.logstream.service.LogServiceImpl;
import com.axlero.logstream.streaming.LiveTailWebSocketHandler;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AsyncLogBatchProcessorTest {

    @TempDir
    Path tempDir;

    private LogIndexer indexer;
    private AsyncLogBatchProcessor processor;

    @BeforeEach
    void setUp() throws IOException {
        indexer = new LogIndexer();
        ReflectionTestUtils.setField(indexer, "indexDir", tempDir.resolve("lucene-test").toString());
        indexer.init();

        LiveTailWebSocketHandler mockWs = new LiveTailWebSocketHandler();

        processor = new AsyncLogBatchProcessor(indexer, mockWs);
        ReflectionTestUtils.setField(processor, "bufferCapacity", 500);
        ReflectionTestUtils.setField(processor, "batchSize", 50);
        ReflectionTestUtils.setField(processor, "flushIntervalMs", 20L);
        ReflectionTestUtils.setField(processor, "websocketSampleRate", 1);
        processor.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (processor != null) {
            processor.flushAndClose();
        }
        if (indexer != null) {
            indexer.close();
        }
    }

    @Test
    void testEnqueueAndBatchFlush() throws InterruptedException, IOException {
        int logCount = 120;
        for (int i = 0; i < logCount; i++) {
            LogEntry entry = new LogEntry(
                    System.currentTimeMillis(),
                    "INFO",
                    "auth-service",
                    25,
                    "Login attempt #" + i
            );
            assertTrue(processor.enqueue(entry), "Log entry should be accepted into buffer");
        }

        assertEquals(logCount, processor.getTotalIngested());

        // Wait for background flusher or manually invoke flushBatch
        processor.flushBatch();
        processor.flushBatch();
        processor.flushBatch();

        indexer.commit();

        assertTrue(processor.getTotalIndexed() >= 100, "Should have indexed buffered batches");
        assertTrue(processor.getBatchesProcessed() >= 1, "Should have processed at least 1 batch");
    }

    @Test
    void testBufferSaturationAndDropTelemetry() {
        // Enqueue more than capacity (500) without flushing
        int totalToInsert = 600;
        int dropped = 0;

        for (int i = 0; i < totalToInsert; i++) {
            LogEntry entry = new LogEntry(
                    System.currentTimeMillis(),
                    "WARN",
                    "rate-limiter",
                    5,
                    "Drop test #" + i
            );
            if (!processor.enqueue(entry)) {
                dropped++;
            }
        }

        assertTrue(dropped > 0, "Buffer overflow should be safely detected and dropped");
        assertEquals(dropped, processor.getTotalDropped());
        assertEquals(500, processor.getBufferCapacity());
    }

    @Test
    void testLogServiceImplStreamingToAsyncProcessor() throws InterruptedException {
        LogServiceImpl service = new LogServiceImpl(processor);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LogResponse> responseRef = new AtomicReference<>();

        StreamObserver<LogResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(LogResponse value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };

        StreamObserver<LogMessage> requestObserver = service.streamLogs(responseObserver);

        int streamCount = 200;
        for (int i = 0; i < streamCount; i++) {
            LogMessage msg = LogMessage.newBuilder()
                    .setTimestamp(Instant.now().toString())
                    .setServiceName("payment-service")
                    .setLevel("INFO")
                    .setResponseTime(120)
                    .setMessage("Payment processed #" + i)
                    .build();
            requestObserver.onNext(msg);
        }

        requestObserver.onCompleted();

        boolean done = latch.await(5, TimeUnit.SECONDS);
        assertTrue(done, "Stream completion latch should release");
        assertNotNull(responseRef.get(), "Response should be received");
        assertTrue(responseRef.get().getSuccess(), "Response success should be true");
        assertTrue(responseRef.get().getStatusMessage().contains("Successfully ingested 200 logs"));
    }
}
