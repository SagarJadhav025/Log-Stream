package com.axlero.logstream.service;

import com.axlero.logstream.grpc.LogMessage;
import com.axlero.logstream.grpc.LogResponse;
import com.axlero.logstream.pipeline.AsyncLogBatchProcessor;
import com.axlero.logstream.pipeline.LogEntry;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LogServiceImplTest {

    private AsyncLogBatchProcessor processor;
    private LogServiceImpl logService;

    @BeforeEach
    void setUp() {
        processor = mock(AsyncLogBatchProcessor.class);
        when(processor.enqueue(any(LogEntry.class))).thenReturn(true);
        logService = new LogServiceImpl(processor);
    }

    private LogEntry captureSingleEntry() {
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(processor).enqueue(captor.capture());
        return captor.getValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEnqueueLogWithParsedIsoTimestamp() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver = logService.streamLogs(responseObserver);

        requestObserver.onNext(LogMessage.newBuilder()
                .setTimestamp("2026-11-23T10:15:30Z")
                .setLevel("ERROR")
                .setServiceName("payment-service")
                .setMessage("Payment processing failed")
                .build());

        LogEntry entry = captureSingleEntry();
        assertEquals(Instant.parse("2026-11-23T10:15:30Z").toEpochMilli(), entry.timestamp());
        assertEquals("ERROR", entry.level());
        assertEquals("payment-service", entry.service());
        assertEquals("Payment processing failed", entry.message());

        verifyNoInteractions(responseObserver);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseCurrentTimeWhenTimestampIsInvalid() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver = logService.streamLogs(responseObserver);

        long before = System.currentTimeMillis();
        requestObserver.onNext(LogMessage.newBuilder()
                .setTimestamp("invalid-timestamp")
                .setLevel("INFO")
                .setServiceName("auth-service")
                .setMessage("User authenticated")
                .build());
        long after = System.currentTimeMillis();

        LogEntry entry = captureSingleEntry();
        assertTrue(entry.timestamp() >= before);
        assertTrue(entry.timestamp() <= after);
        assertEquals("auth-service", entry.service());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseCurrentTimeWhenTimestampIsBlank() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver = logService.streamLogs(responseObserver);

        long before = System.currentTimeMillis();
        requestObserver.onNext(LogMessage.newBuilder()
                .setTimestamp("   ")
                .setLevel("WARN")
                .setServiceName("inventory-service")
                .setMessage("Low stock warning")
                .build());
        long after = System.currentTimeMillis();

        LogEntry entry = captureSingleEntry();
        assertTrue(entry.timestamp() >= before);
        assertTrue(entry.timestamp() <= after);
        assertEquals("WARN", entry.level());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDefaultLevelAndServiceWhenMissing() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver = logService.streamLogs(responseObserver);

        requestObserver.onNext(LogMessage.newBuilder().setMessage("bare log").build());

        LogEntry entry = captureSingleEntry();
        assertEquals("INFO", entry.level());
        assertEquals("default-service", entry.service());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnSuccessfulResponseWhenStreamCompletes() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver = logService.streamLogs(responseObserver);

        requestObserver.onNext(LogMessage.newBuilder()
                .setLevel("INFO").setServiceName("auth-service").setMessage("First log").build());
        requestObserver.onNext(LogMessage.newBuilder()
                .setLevel("ERROR").setServiceName("payment-service").setMessage("Second log").build());
        requestObserver.onCompleted();

        ArgumentCaptor<LogResponse> responseCaptor = ArgumentCaptor.forClass(LogResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        LogResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertEquals("Successfully ingested 2 logs (Enqueued: 2).", response.getStatusMessage());

        verify(processor, times(2)).enqueue(any(LogEntry.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReportDroppedLogsWhenQueueRejects() {
        when(processor.enqueue(any(LogEntry.class))).thenReturn(false);

        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver = logService.streamLogs(responseObserver);

        requestObserver.onNext(LogMessage.newBuilder().setMessage("dropped").build());
        requestObserver.onCompleted();

        ArgumentCaptor<LogResponse> responseCaptor = ArgumentCaptor.forClass(LogResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        assertEquals("Successfully ingested 1 logs (Enqueued: 0).",
                responseCaptor.getValue().getStatusMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotSendSuccessResponseWhenStreamErrors() {
        StreamObserver<LogResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<LogMessage> requestObserver = logService.streamLogs(responseObserver);

        requestObserver.onError(new RuntimeException("Simulated stream failure"));

        verifyNoInteractions(responseObserver);
        verifyNoInteractions(processor);
    }
}