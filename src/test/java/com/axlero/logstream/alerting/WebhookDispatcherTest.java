package com.axlero.logstream.alerting;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WebhookDispatcherTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String start(int status, AtomicInteger hits, AtomicReference<String> body) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", ex -> {
            hits.incrementAndGet();
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(status, -1);
            ex.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/hook";
    }

    @Test
    void deliversJsonOnSuccess() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        String url = start(200, hits, body);

        AlertNotifier notifier = new AlertNotifier(new WebhookDispatcher(url, 3, 10));
        assertTrue(notifier.dispatch(new AlertEvent("high-errors", "level:ERROR", 150, 100, Instant.now())));

        assertEquals(1, hits.get());
        assertTrue(body.get().contains("\"ruleName\":\"high-errors\""));
        assertTrue(body.get().contains("\"count\":150"));
    }

    @Test
    void retriesThenGivesUpOnServerError() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        String url = start(500, hits, new AtomicReference<>());

        WebhookDispatcher d = new WebhookDispatcher(url, 2, 10);
        assertFalse(d.send("{}"));
        assertEquals(2, hits.get());
    }

    @Test
    void escapesQuotesInJson() {
        String json = new AlertEvent("r", "message:\"timeout\"", 1, 1, Instant.now()).toJson();
        assertTrue(json.contains("message:\\\"timeout\\\""));
    }
}
