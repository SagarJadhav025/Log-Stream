package com.axlero.logstream.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * High-performance WebSocket handler for the Live Tail log stream.
 * Mapped to /ws/logs.
 * Broadcasts incoming logs to all active UI sessions.
 */
@Component
public class LiveTailWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(LiveTailWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final Random random = new Random();

    private static final String[] DEMO_SERVICES = {
            "auth-service", "billing-api", "payment-gateway", "order-processor",
            "inventory-db", "user-service", "notification-svc", "gateway-proxy"
    };

    private static final String[] DEMO_MESSAGES = {
            "GET /v1/health status:200 duration:12ms",
            "Processed transaction checkout successfully",
            "Connection acquired from HikariPool-1",
            "Slow query detected: SELECT * FROM orders WHERE user_id = ? took 482ms",
            "Cache miss for key user:session:84820; fetching from Redis",
            "JWT token validated successfully for sub:usr_99182",
            "High latency warning on downstream payment gateway timeout: 2100ms",
            "Database connection reset by peer during replica sync",
            "User failed authentication: invalid credentials for email=admin@axlero.com",
            "Batch payload dispatched to Lucene index writer"
    };

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        logger.info("Live Tail WebSocket client connected: id={}, total_clients={}", session.getId(), sessions.size());

        // Send an initial handshake log
        String welcome = formatLogJson(
                System.currentTimeMillis(),
                "INFO",
                "logstream-daemon",
                "Live tail stream established. Connected to cluster logstream."
        );
        sendToSession(session, welcome);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        logger.info("Live Tail WebSocket client disconnected: id={}, remaining_clients={}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        logger.debug("Received message from client {}: {}", session.getId(), message.getPayload());
    }

    /**
     * Broadcasts a log entry to all connected UI clients.
     */
    public void broadcastLog(long timestampEpochMillis, String level, String service, String message) {
        if (sessions.isEmpty()) {
            return;
        }

        String json = formatLogJson(timestampEpochMillis, level, service, message);
        TextMessage textMessage = new TextMessage(json);

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    logger.warn("Failed to send log to WebSocket session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Lightweight background demo heartbeat that streams live telemetry logs
     * if active clients are connected.
     * Healthy systems stream INFO telemetry. Real ERROR/WARN events come from
     * actual ingestion or deliberate test simulations.
     */
    @Scheduled(fixedDelay = 2000)
    public void emitHeartbeatLogs() {
        if (sessions.isEmpty()) {
            return;
        }

        String service = DEMO_SERVICES[random.nextInt(DEMO_SERVICES.length)];
        String msg = DEMO_MESSAGES[random.nextInt(3)]; // Standard health/connection messages

        // Emit telemetry heartbeat as INFO so live tail receives stream without spamming alerts
        broadcastLog(Instant.now().toEpochMilli(), "INFO", service, msg);
    }

    private void sendToSession(WebSocketSession session, String payload) {
        if (session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                logger.warn("Error sending initial message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private String formatLogJson(long timestamp, String level, String service, String message) {
        return "{" +
                "\"timestamp\":" + timestamp + "," +
                "\"level\":\"" + escapeJson(level) + "\"," +
                "\"service\":\"" + escapeJson(service) + "\"," +
                "\"message\":\"" + escapeJson(message) + "\"" +
                "}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
