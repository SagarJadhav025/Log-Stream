package com.axlero.logstream.service;

import com.axlero.logstream.grpc.LogIngestionServiceGrpc;
import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @GrpcService tells Spring Boot to automatically start hosting this on the gRPC port
@GrpcService
public class LogServiceImpl extends LogIngestionServiceGrpc.LogIngestionServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    @Override
    public StreamObserver<LogMessage> streamLogs(StreamObserver<LogResponse> responseObserver) {
        return new StreamObserver<LogMessage>() {
            private int logCount = 0;

            @Override
            public void onNext(LogMessage logMessage) {
                // For now, we just count the logs to prepare for your 10,000/sec throughput audit
                logCount++;
                if (logCount % 5000 == 0) {
                    logger.info("Received {} logs so far...", logCount);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error receiving log stream", t);
            }

            @Override
            public void onCompleted() {
                logger.info("Stream finished. Total logs received: {}", logCount);

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