package com.axlero.logstream.service;

import com.axlero.logstream.grpc.LogIngestionServiceGrpc;
import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class LogServiceImpl extends LogIngestionServiceGrpc.LogIngestionServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    @Override
    public StreamObserver<LogMessage> streamLogs(StreamObserver<LogResponse> responseObserver) {
        return new StreamObserver<LogMessage>() {
            private int logCount = 0;

            @Override
            public void onNext(LogMessage logMessage) {
                logCount++;

                // 1. Extract the actual log data (adjust getter names to match your .proto file)
                // String serviceName = logMessage.getServiceName();
                // String logLevel = logMessage.getLevel();
                // String messageContent = logMessage.getMessage();
                // long timestamp = logMessage.getTimestamp();

                // 2. Pass the extracted data to your Lucene Indexer for real-time search
                // indexService.indexLogDocument(serviceName, logLevel, messageContent, timestamp);

                // 3. High-throughput batch logging
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