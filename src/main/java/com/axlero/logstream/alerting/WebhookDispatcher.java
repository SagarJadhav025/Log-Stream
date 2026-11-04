package com.axlero.logstream.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class WebhookDispatcher {
    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final String url;
    private final int maxAttempts;
    private final long backoffMs;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public WebhookDispatcher(
            @Value("${logstream.alert.webhook-url:http://localhost:8080/api/alerts/mock-receiver}") String url,
            @Value("${logstream.alert.max-attempts:3}") int maxAttempts,
            @Value("${logstream.alert.backoff-ms:500}") long backoffMs) {
        this.url = url;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /** Returns true if the webhook answered with a 2xx status within the allowed attempts. */
    public boolean send(String json) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    log.info("Webhook delivered to {} (attempt {})", url, attempt);
                    return true;
                }
                log.warn("Webhook {} returned HTTP {} (attempt {}/{})", url, resp.statusCode(), attempt, maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.warn("Webhook {} failed (attempt {}/{}): {}", url, attempt, maxAttempts, e.toString());
            }
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        log.error("Webhook {} gave up after {} attempts", url, maxAttempts);
        return false;
    }
}
