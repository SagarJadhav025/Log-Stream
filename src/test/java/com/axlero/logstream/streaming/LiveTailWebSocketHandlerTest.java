
package com.axlero.logstream.streaming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LiveTailWebSocketHandlerTest {

    private LiveTailWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        handler = new LiveTailWebSocketHandler();
        session = mock(WebSocketSession.class);

        when(session.getId()).thenReturn("test-session");
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    void shouldRegisterSessionAndSendWelcomeMessageOnConnection() throws Exception {
        handler.afterConnectionEstablished(session);

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldRemoveSessionWhenConnectionCloses() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        handler.broadcastLog(
                1730000000000L,
                "INFO",
                "test-service",
                "Test message"
        );

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldBroadcastLogToConnectedSession() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        handler.broadcastLog(
                1730000000000L,
                "INFO",
                "auth-service",
                "User authenticated successfully"
        );

        verify(session).sendMessage(argThat((WebSocketMessage<?> message) -> {
            String payload = ((TextMessage) message).getPayload();

            return payload.contains("\"level\":\"INFO\"")
                    && payload.contains("\"service\":\"auth-service\"")
                    && payload.contains("User authenticated successfully");
        }));
    }

    @Test
    void shouldNotSendLogToClosedSession() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        when(session.isOpen()).thenReturn(false);

        handler.broadcastLog(
                1730000000000L,
                "ERROR",
                "payment-service",
                "Payment failed"
        );

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldHandleIOExceptionDuringBroadcast() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        doThrow(new IOException("Connection lost"))
                .when(session).sendMessage(any(TextMessage.class));

        assertDoesNotThrow(() -> handler.broadcastLog(
                1730000000000L,
                "WARN",
                "gateway-service",
                "Gateway timeout"
        ));

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldEscapeSpecialCharactersInLogMessage() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        handler.broadcastLog(
                1730000000000L,
                "INFO",
                "test-service",
                "First line\nSecond line with \"quotes\" and \\ slash"
        );

        verify(session).sendMessage(argThat((WebSocketMessage<?> message) -> {
            String payload = ((TextMessage) message).getPayload();

            return payload.contains("First line\\nSecond line")
                    && payload.contains("\\\"quotes\\\"")
                    && payload.contains("\\\\ slash");
        }));
    }

    @Test
    void shouldHandleNullLogFields() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        handler.broadcastLog(
                1730000000000L,
                null,
                null,
                null
        );

        verify(session).sendMessage(argThat((WebSocketMessage<?> message) -> {
            String payload = ((TextMessage) message).getPayload();

            return payload.contains("\"level\":\"\"")
                    && payload.contains("\"service\":\"\"")
                    && payload.contains("\"message\":\"\"");
        }));
    }

    @Test
    void shouldNotGenerateHeartbeatWhenNoClientsAreConnected() {
        assertDoesNotThrow(() -> handler.emitHeartbeatLogs());

        verifyNoInteractions(session);
    }

    @Test
    void shouldBroadcastHeartbeatWhenClientIsConnected() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        handler.emitHeartbeatLogs();

        verify(session).sendMessage(argThat((WebSocketMessage<?> message) -> {
            String payload = ((TextMessage) message).getPayload();

            return payload.contains("\"timestamp\":")
                    && payload.contains("\"level\":")
                    && payload.contains("\"service\":")
                    && payload.contains("\"message\":");
        }));
    }
}
