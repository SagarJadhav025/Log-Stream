package com.axlero.logstream.benchmark;

import com.axlero.logstream.grpc.LogIngestionServiceGrpc;
import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Day 6: Stress Test — simulates 10 concurrent gRPC workers blasting 100,000 logs each
 * for a total of 1,000,000 logs. Verifies >10,000 logs/sec sustained ingestion throughput.
 */
public class LogIngestionStressTest {

    private static final String[] SERVICES = {"order-service", "payment-service", "auth-service", "inventory-service", "gateway-proxy"};
    private static final String[] LEVELS = {"INFO", "INFO", "INFO", "WARN", "ERROR", "DEBUG"};

    public static void main(String[] args) throws Exception {
        int workerCount = 10;
        int logsPerWorker = 100_000;
        int totalLogs = workerCount * logsPerWorker;

        System.out.println("=".repeat(60));
        System.out.println("🚀 LogStream Ingestion Stress Test");
        System.out.println("   Workers: " + workerCount + "  |  Logs/Worker: " + String.format("%,d", logsPerWorker));
        System.out.println("   Target Total: " + String.format("%,d", totalLogs) + " logs");
        System.out.println("=".repeat(60));

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), "super-secret-ingestor-key-2026");

        AtomicLong totalSent = new AtomicLong(0);
        AtomicLong totalErrors = new AtomicLong(0);
        CountDownLatch allDone = new CountDownLatch(workerCount);
        List<Long> workerTimes = new CopyOnWriteArrayList<>();

        long globalStart = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        for (int w = 0; w < workerCount; w++) {
            final int workerId = w;
            executor.submit(() -> {
                long workerStart = System.currentTimeMillis();
                Random rand = new Random();
                CountDownLatch workerLatch = new CountDownLatch(1);

                LogIngestionServiceGrpc.LogIngestionServiceStub stub = MetadataUtils.newAttachHeadersInterceptor(metadata)
                        .interceptCall(null, null, null) == null
                        ? LogIngestionServiceGrpc.newStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                        : LogIngestionServiceGrpc.newStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

                StreamObserver<LogResponse> responseObserver = new StreamObserver<>() {
                    @Override public void onNext(LogResponse value) {}
                    @Override public void onError(Throwable t) {
                        totalErrors.incrementAndGet();
                        workerLatch.countDown();
                    }
                    @Override public void onCompleted() { workerLatch.countDown(); }
                };

                StreamObserver<LogMessage> requestObserver = stub.streamLogs(responseObserver);

                for (int i = 0; i < logsPerWorker; i++) {
                    requestObserver.onNext(LogMessage.newBuilder()
                            .setTimestamp(Instant.now().toString())
                            .setServiceName(SERVICES[rand.nextInt(SERVICES.length)])
                            .setLevel(LEVELS[rand.nextInt(LEVELS.length)])
                            .setResponseTime(10 + rand.nextInt(490))
                            .setMessage("Stress log w" + workerId + "-i" + i + " transaction processed")
                            .build());
                    totalSent.incrementAndGet();
                }

                requestObserver.onCompleted();

                try { workerLatch.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                long workerMs = System.currentTimeMillis() - workerStart;
                workerTimes.add(workerMs);
                allDone.countDown();
            });
        }

        boolean finished = allDone.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        long totalMs = Math.max(1, System.currentTimeMillis() - globalStart);
        double throughput = (totalSent.get() * 1000.0) / totalMs;

        System.out.println("\n📊 Ingestion Stress Test Results:");
        System.out.printf("   Total Logs Sent  : %,d%n", totalSent.get());
        System.out.printf("   Total Errors     : %,d%n", totalErrors.get());
        System.out.printf("   Wall-clock Time  : %,d ms%n", totalMs);
        System.out.printf("   Throughput       : %,.1f logs/sec%n", throughput);
        System.out.printf("   Target Met (10k) : %s%n", throughput >= 10_000 ? "✅ YES" : "❌ NO — tune buffer/batch");
        System.out.printf("   Completed        : %s%n", finished ? "✅ All workers done" : "⚠ Timed out");

        channel.shutdown();
    }
}
