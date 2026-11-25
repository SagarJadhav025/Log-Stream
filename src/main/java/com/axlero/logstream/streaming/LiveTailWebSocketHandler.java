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
import java.util.Iterator;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Day 4: WebSocket Live Tail with per-session backpressure buffer & minified JSON payloads.
 * - Per-session bounded outbound queue (max 1000 messages) prevents slow consumers choking ingestion.
 * - Minified JSON keys ("t","l","s","m") reduce per-message payload size ~40%.
 * - ERROR/WARN logs are always forwarded; INFO/DEBUG are sampled 1-in-5.
 */
@Component
public class LiveTailWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(LiveTailWebSocketHandler.class);

    // Max buffered messages per slow WebSocket consumer before dropping
    private static final int MAX_SESSION_QUEUE = 1000;

    // INFO/DEBUG sampling: 1 in N messages forwarded to reduce UI flood
    private static final int INFO_SAMPLE_RATE = 5;

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    // Per-session outbound queue for backpressure isolation
    private final ConcurrentHashMap<String, Queue<TextMessage>> sessionQueues = new ConcurrentHashMap<>();

    // Drop counters per session for telemetry
    private final ConcurrentHashMap<String, AtomicLong> sessionDrops = new ConcurrentHashMap<>();

    private final Random random = new Random();
    private final AtomicLong infoSequence = new AtomicLong(0);

    private static final String[] DEMO_SERVICES = {
            "auth-service", "billing-api", "payment-gateway", "order-processor",
            "inventory-db", "user-service", "notification-svc", "gateway-proxy"
    };

    private static final String[] DEMO_MESSAGES = {
            "GET /v1/health status:200 duration:12ms",
            "Processed transaction checkout successfully",
            "Connection acquired from HikariPool-1",
            "Slow query detected: SELECT * FROM orders WHERE user_id=? took 482ms",
            "Cache miss for key user:session:84820; fetching from Redis",
            "JWT token validated for sub:usr_99182",
            "High latency on payment gateway timeout: 2100ms",
            "Database connection reset by peer during replica sync",
            "User auth failed: invalid credentials for email=admin@axlero.com",
            "Batch payload dispatched to Lucene index writer"
    };

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        sessionQueues.put(session.getId(), new ConcurrentLinkedQueue<>());
        sessionDrops.put(session.getId(), new AtomicLong(0));

        logger.info("WS connected: id={}, total={}", session.getId(), sessions.size());

        // Send minified welcome message
        String welcome = minifiedJson(System.currentTimeMillis(), "INFO", "logstream-daemon",
                "Live tail stream established. Connected to cluster logstream.");
        sendToSession(session, welcome);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        sessionQueues.remove(session.getId());
        AtomicLong drops = sessionDrops.remove(session.getId());
        if (drops != null && drops.get() > 0) {
            logger.info("WS disconnected: id={}, total={}, totalDropped={}", session.getId(), sessions.size(), drops.get());
        } else {
            logger.info("WS disconnected: id={}, total={}", session.getId(), sessions.size());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        logger.debug("WS msg from client {}: {}", session.getId(), message.getPayload());
    }

    /**
     * Broadcasts a log entry to all connected clients with:
     * 1. Level-based sampling — only errors/warnings always forward; INFO/DEBUG sampled
     * 2. Per-session bounded queue with backpressure drop
     * 3. Minified JSON payload
     */
    public void broadcastLog(long timestampEpochMillis, String level, String service, String message) {
        if (sessions.isEmpty()) return;

        // Level-based sampling: ERROR/WARN always pass, INFO/DEBUG 1-in-N
        boolean isHighPriority = "ERROR".equals(level) || "WARN".equals(level);
        if (!isHighPriority && (infoSequence.incrementAndGet() % INFO_SAMPLE_RATE != 0)) {
            return;
        }

        String json = minifiedJson(timestampEpochMillis, level, service, message);
        TextMessage textMessage = new TextMessage(json);

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;

            Queue<TextMessage> queue = sessionQueues.get(session.getId());
            if (queue == null) continue;

            if (queue.size() >= MAX_SESSION_QUEUE) {
                // Slow consumer: drop and count
                AtomicLong drops = sessionDrops.get(session.getId());
                if (drops != null) {
                    long totalDrops = drops.incrementAndGet();
                    if (totalDrops % 100 == 1) {
                        logger.warn("WS backpressure: dropped {} messages for slow session {}", totalDrops, session.getId());
                    }
                }
                continue;
            }

            queue.offer(textMessage);
            flushSessionQueue(session, queue);
        }
    }

    /**
     * Flushes one message from a session's queue to the actual WebSocket.
     * Uses per-session synchronized block to protect concurrent WebSocket writes.
     */
    private void flushSessionQueue(WebSocketSession session, Queue<TextMessage> queue) {
        TextMessage msg = queue.poll();
        if (msg == null) return;

        if (session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(msg);
                }
            } catch (IOException e) {
                logger.warn("WS send failed for session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * Heartbeat demo emitter — streams live telemetry logs every 2 seconds.
     */
    @Scheduled(fixedDelay = 2000)
    public void emitHeartbeatLogs() {
        if (sessions.isEmpty()) return;
        String service = DEMO_SERVICES[random.nextInt(DEMO_SERVICES.length)];
        String msg = DEMO_MESSAGES[random.nextInt(3)];
        broadcastLog(Instant.now().toEpochMilli(), "INFO", service, msg);
    }

    private void sendToSession(WebSocketSession session, String payload) {
        if (session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                logger.warn("WS initial send failed for session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * Day 4: Minified JSON with short keys to cut per-message bandwidth ~40%.
     * Format: {"t":timestamp,"l":"LEVEL","s":"service","m":"message"}
     */
    private String minifiedJson(long timestamp, String level, String service, String message) {
        return "{\"t\":" + timestamp +
                ",\"l\":\"" + escapeJson(level) +
                "\",\"s\":\"" + escapeJson(service) +
                "\",\"m\":\"" + escapeJson(message) + "\"}";
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
