package com.axlero.logstream.service;

import com.axlero.logstream.grpc.LogIngestionServiceGrpc;
import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import com.axlero.logstream.streaming.LiveTailWebSocketHandler;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@GrpcService
public class LogServiceImpl extends LogIngestionServiceGrpc.LogIngestionServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    private final LiveTailWebSocketHandler liveTailWebSocketHandler;

    public LogServiceImpl(LiveTailWebSocketHandler liveTailWebSocketHandler) {
        this.liveTailWebSocketHandler = liveTailWebSocketHandler;
    }

    @Override
    public StreamObserver<LogMessage> streamLogs(StreamObserver<LogResponse> responseObserver) {
        return new StreamObserver<LogMessage>() {
            private int logCount = 0;

            @Override
            public void onNext(LogMessage logMessage) {
                logCount++;

                // Broadcast directly to Live Tail WebSocket subscribers
                long ts = System.currentTimeMillis();
                try {
                    if (logMessage.getTimestamp() != null && !logMessage.getTimestamp().isBlank()) {
                        ts = Instant.parse(logMessage.getTimestamp()).toEpochMilli();
                    }
                } catch (Exception ignored) {
                }

                liveTailWebSocketHandler.broadcastLog(
                        ts,
                        logMessage.getLevel(),
                        logMessage.getServiceName(),
                        logMessage.getMessage()
                );

                // High-throughput batch logging
                if (logCount % 5000 == 0) {
                    logger.info("High-throughput ingestion: Received {} logs so far...", logCount);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error receiving gRPC log stream: ", t);
            }

            @Override
            public void onCompleted() {
                logger.info("Stream finished. Total logs successfully ingested: {}", logCount);

                LogResponse response = LogResponse.newBuilder()
                        .setSuccess(true)
                        .setStatusMessage("Successfully ingested " + logCount + " logs.")
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }
}
