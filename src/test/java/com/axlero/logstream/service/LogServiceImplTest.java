
package com.axlero.logstream.service;

import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import com.axlero.logstream.streaming.LiveTailWebSocketHandler;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogServiceImplTest {

    private LiveTailWebSocketHandler liveTailWebSocketHandler;
    private LogServiceImpl logService;

    @BeforeEach
    void setUp() {
        liveTailWebSocketHandler = mock(LiveTailWebSocketHandler.class);
        logService = new LogServiceImpl(liveTailWebSocketHandler);
    }

    @Test
    void shouldBroadcastIncomingLog() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver =
                logService.streamLogs(responseObserver);

        LogMessage message = LogMessage.newBuilder()
                .setTimestamp("2026-11-23T10:15:30Z")
                .setLevel("ERROR")
                .setServiceName("payment-service")
                .setMessage("Payment processing failed")
                .build();

        requestObserver.onNext(message);

        verify(liveTailWebSocketHandler).broadcastLog(
                Instant.parse("2026-11-23T10:15:30Z").toEpochMilli(),
                "ERROR",
                "payment-service",
                "Payment processing failed"
        );

        verifyNoInteractions(responseObserver);
    }

    @Test
    void shouldUseCurrentTimeWhenTimestampIsInvalid() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver =
                logService.streamLogs(responseObserver);

        long beforeCall = System.currentTimeMillis();

        LogMessage message = LogMessage.newBuilder()
                .setTimestamp("invalid-timestamp")
                .setLevel("INFO")
                .setServiceName("auth-service")
                .setMessage("User authenticated")
                .build();

        requestObserver.onNext(message);

        long afterCall = System.currentTimeMillis();

        org.mockito.ArgumentCaptor<Long> timestampCaptor =
                org.mockito.ArgumentCaptor.forClass(Long.class);

        verify(liveTailWebSocketHandler).broadcastLog(
                timestampCaptor.capture(),
                eq("INFO"),
                eq("auth-service"),
                eq("User authenticated")
        );

        assertTrue(timestampCaptor.getValue() >= beforeCall);
        assertTrue(timestampCaptor.getValue() <= afterCall);
    }

    @Test
    void shouldUseCurrentTimeWhenTimestampIsBlank() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver =
                logService.streamLogs(responseObserver);

        long beforeCall = System.currentTimeMillis();

        LogMessage message = LogMessage.newBuilder()
                .setTimestamp("   ")
                .setLevel("WARN")
                .setServiceName("inventory-service")
                .setMessage("Low stock warning")
                .build();

        requestObserver.onNext(message);

        long afterCall = System.currentTimeMillis();

        org.mockito.ArgumentCaptor<Long> timestampCaptor =
                org.mockito.ArgumentCaptor.forClass(Long.class);

        verify(liveTailWebSocketHandler).broadcastLog(
                timestampCaptor.capture(),
                eq("WARN"),
                eq("inventory-service"),
                eq("Low stock warning")
        );

        assertTrue(timestampCaptor.getValue() >= beforeCall);
        assertTrue(timestampCaptor.getValue() <= afterCall);
    }

    @Test
    void shouldReturnSuccessfulResponseWhenStreamCompletes() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver =
                logService.streamLogs(responseObserver);

        requestObserver.onNext(LogMessage.newBuilder()
                .setLevel("INFO")
                .setServiceName("auth-service")
                .setMessage("First log")
                .build());

        requestObserver.onNext(LogMessage.newBuilder()
                .setLevel("ERROR")
                .setServiceName("payment-service")
                .setMessage("Second log")
                .build());

        requestObserver.onCompleted();

        org.mockito.ArgumentCaptor<LogResponse> responseCaptor =
                org.mockito.ArgumentCaptor.forClass(LogResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        LogResponse response = responseCaptor.getValue();

        assertTrue(response.getSuccess());
        assertEquals("Successfully ingested 2 logs.",
                response.getStatusMessage());

        verify(liveTailWebSocketHandler, times(2))
                .broadcastLog(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotSendSuccessResponseWhenStreamErrors() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver =
                logService.streamLogs(responseObserver);

        requestObserver.onError(new RuntimeException("Simulated stream failure"));

        verifyNoInteractions(responseObserver);
        verifyNoInteractions(liveTailWebSocketHandler);
    }
}
