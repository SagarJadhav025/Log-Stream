package com.axlero.logstream;

import com.axlero.logstream.grpc.LogIngestionServiceGrpc;
import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LoadTestClient {

    private static final String[] SERVICES = {
            "order-service", "payment-service", "auth-service", "inventory-service", "gateway-proxy"
    };

    private static final String[] LEVELS = {"INFO", "DEBUG", "WARN", "ERROR"};

    public static void main(String[] args) throws InterruptedException {
        // 1. Connect to Spring Boot gRPC server (default port 9090)
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        // 2. Attach x-api-key authentication metadata
        Metadata metadata = new Metadata();
        Metadata.Key<String> apiKeyHeader = Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(apiKeyHeader, "super-secret-ingestor-key-2026");

        LogIngestionServiceGrpc.LogIngestionServiceStub stub = LogIngestionServiceGrpc.newStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<LogResponse> responseObserver = new StreamObserver<LogResponse>() {
            @Override
            public void onNext(LogResponse response) {
                System.out.println("SERVER RESPONSE: " + response.getStatusMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Ingestion error: " + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        };

        // 3. Open streaming connection
        StreamObserver<LogMessage> requestObserver = stub.streamLogs(responseObserver);

        // 4. Send 50,000 realistic logs to benchmark throughput
        int totalLogs = 50000;
        System.out.println("Starting high-throughput load test: Sending " + totalLogs + " logs...");
        long startTime = System.currentTimeMillis();
        Random random = new Random();

        for (int i = 0; i < totalLogs; i++) {
            String service = SERVICES[random.nextInt(SERVICES.length)];
            String level = LEVELS[random.nextInt(LEVELS.length)];
            int responseTime = 10 + random.nextInt(490);
            String message = "Handled HTTP request " + i + " service=" + service + " status=200 response_time=" + responseTime + "ms";

            LogMessage messageProto = LogMessage.newBuilder()
                    .setTimestamp(Instant.now().toString())
                    .setServiceName(service)
                    .setLevel(level)
                    .setResponseTime(responseTime)
                    .setMessage(message)
                    .build();

            requestObserver.onNext(messageProto);
        }

        // 5. Complete stream and calculate throughput
        requestObserver.onCompleted();
        boolean completed = finishLatch.await(30, TimeUnit.SECONDS);

        long timeTaken = Math.max(1, System.currentTimeMillis() - startTime);
        double throughput = (totalLogs * 1000.0) / timeTaken;

        System.out.println("=================================================");
        System.out.printf("✅ Sent %,d logs in %,d ms%n", totalLogs, timeTaken);
        System.out.printf("🚀 Ingestion Throughput: %,.2f logs/second%n", throughput);
        System.out.println("Completed successfully: " + completed);
        System.out.println("=================================================");

        channel.shutdown();
    }
}
