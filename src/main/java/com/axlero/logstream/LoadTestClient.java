package com.axlero.logstream;

import com.axlero.logstream.grpc.LogIngestionServiceGrpc;
import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LoadTestClient {
    public static void main(String[] args) throws InterruptedException {
        // 1. Connect to your running Spring Boot gRPC server (default port 9090)
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        LogIngestionServiceGrpc.LogIngestionServiceStub stub = LogIngestionServiceGrpc.newStub(channel);
        CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<LogResponse> responseObserver = new StreamObserver<LogResponse>() {
            @Override
            public void onNext(LogResponse response) {
                System.out.println("SERVER RESPONSE: " + response.getStatusMessage());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        };

        // 2. Open the connection stream
        StreamObserver<LogMessage> requestObserver = stub.streamLogs(responseObserver);

        // 3. Blast 50,000 logs as fast as possible to test the 10k/sec requirement
        int totalLogs = 50000;
        System.out.println("Starting high-throughput load test: Sending " + totalLogs + " logs...");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalLogs; i++) {
            // Creating an empty log message just to test the server's counting speed
            LogMessage message = LogMessage.newBuilder().build();
            requestObserver.onNext(message);
        }

        // 4. Close the stream and calculate the time
        requestObserver.onCompleted();
        finishLatch.await(10, TimeUnit.SECONDS);

        long timeTaken = System.currentTimeMillis() - startTime;
        System.out.println("✅ Sent " + totalLogs + " logs in " + timeTaken + " milliseconds!");

        channel.shutdown();
    }
}