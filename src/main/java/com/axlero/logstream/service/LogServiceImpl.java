package com.axlero.logstream.service;

import com.axlero.logstream.grpc.LogIngestionServiceGrpc;
import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import com.axlero.logstream.pipeline.AsyncLogBatchProcessor;
import com.axlero.logstream.pipeline.LogEntry;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@GrpcService
public class LogServiceImpl extends LogIngestionServiceGrpc.LogIngestionServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    private final AsyncLogBatchProcessor asyncLogBatchProcessor;

    public LogServiceImpl(AsyncLogBatchProcessor asyncLogBatchProcessor) {
        this.asyncLogBatchProcessor = asyncLogBatchProcessor;
    }

    @Override
    public StreamObserver<LogMessage> streamLogs(StreamObserver<LogResponse> responseObserver) {
        return new StreamObserver<LogMessage>() {
            private final AtomicInteger receivedCount = new AtomicInteger(0);
            private final AtomicInteger enqueuedCount = new AtomicInteger(0);

            @Override
            public void onNext(LogMessage logMessage) {
                int current = receivedCount.incrementAndGet();

                long timestamp = System.currentTimeMillis();
                try {
                    if (logMessage.getTimestamp() != null && !logMessage.getTimestamp().isBlank()) {
                        String rawTs = logMessage.getTimestamp().trim();
                        if (rawTs.chars().allMatch(Character::isDigit)) {
                            timestamp = Long.parseLong(rawTs);
                        } else {
                            timestamp = Instant.parse(rawTs).toEpochMilli();
                        }
                    }
                } catch (Exception ignored) {
                }

                String level = (logMessage.getLevel() != null && !logMessage.getLevel().isBlank())
                        ? logMessage.getLevel() : "INFO";
                String service = (logMessage.getServiceName() != null && !logMessage.getServiceName().isBlank())
                        ? logMessage.getServiceName() : "default-service";
                String message = logMessage.getMessage() != null ? logMessage.getMessage() : "";
                long responseTime = logMessage.getResponseTime();

                LogEntry entry = new LogEntry(timestamp, level, service, responseTime, message);
                boolean accepted = asyncLogBatchProcessor.enqueue(entry);
                if (accepted) {
                    enqueuedCount.incrementAndGet();
                }

                if (current % 10000 == 0) {
                    logger.info("High-throughput gRPC ingestion: Received {} logs, Buffered {} (Queue depth: {})...",
                            current, enqueuedCount.get(), asyncLogBatchProcessor.getQueueDepth());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error receiving gRPC log stream: ", t);
            }

            @Override
            public void onCompleted() {
                int totalReceived = receivedCount.get();
                int totalEnqueued = enqueuedCount.get();

                logger.info("Stream finished. Total logs received: {}, enqueued: {}", totalReceived, totalEnqueued);

                LogResponse response = LogResponse.newBuilder()
                        .setSuccess(true)
                        .setStatusMessage("Successfully ingested " + totalReceived + " logs (Enqueued: " + totalEnqueued + ").")
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }
}
