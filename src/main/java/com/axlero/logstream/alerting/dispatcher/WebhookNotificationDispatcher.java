package com.axlero.logstream.alerting.dispatcher;

import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import com.axlero.logstream.alerting.model.NotificationPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dispatches alert breach events as HTTP POST JSON webhooks.
 */
@Component
public class WebhookNotificationDispatcher implements NotificationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(WebhookNotificationDispatcher.class);

    private final HttpClient httpClient;

    public WebhookNotificationDispatcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public WebhookNotificationDispatcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean supports(AlertRule rule) {
        return rule.webhookUrl() != null && !rule.webhookUrl().isBlank();
    }

    @Override
    public String getChannelName() {
        return "WEBHOOK";
    }

    @Override
    public NotificationDeliveryRecord dispatch(AlertBreachEvent breach, AlertRule rule) {
        String webhookUrl = rule.webhookUrl().trim();
        NotificationPayload payload = NotificationPayload.fromBreach(breach);

        try {
            String jsonBody = payload.toJson();

            // SSRF Protection: Block cloud metadata and invalid schemes
            if (!webhookUrl.startsWith("mock://") && !webhookUrl.startsWith("simulated://")) {
                java.net.URI uri = java.net.URI.create(webhookUrl);
                String host = uri.getHost();
                if (host == null || host.equalsIgnoreCase("169.254.169.254") || host.equalsIgnoreCase("metadata.google.internal")) {
                    return NotificationDeliveryRecord.of(
                            rule.id(), getChannelName(), webhookUrl, false, 400, "Blocked dangerous or internal destination URL (SSRF Defense)");
                }
            }

            // Support simulated webhook targets without requiring external network
            if (webhookUrl.startsWith("mock://") || webhookUrl.startsWith("simulated://")) {
                logger.info("Simulated Webhook dispatched to '{}': {}", webhookUrl, jsonBody);
                return NotificationDeliveryRecord.of(
                        rule.id(),
                        getChannelName(),
                        webhookUrl,
                        true,
                        200,
                        "Simulated webhook delivered successfully"
                );
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "LogStream-Alert-Dispatcher/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            boolean success = statusCode >= 200 && statusCode < 300;

            if (success) {
                logger.info("Webhook delivered successfully to '{}' [HTTP {}] for rule '{}'",
                        webhookUrl, statusCode, rule.name());
            } else {
                logger.warn("Webhook delivery failed to '{}' [HTTP {}] for rule '{}': {}",
                        webhookUrl, statusCode, rule.name(), response.body());
            }

            return NotificationDeliveryRecord.of(
                    rule.id(),
                    getChannelName(),
                    webhookUrl,
                    success,
                    statusCode,
                    success ? "Delivered successfully" : "HTTP error response: " + statusCode
            );

        } catch (Exception e) {
            logger.error("Exception during webhook dispatch to '{}' for rule '{}': {}",
                    webhookUrl, rule.name(), e.getMessage());

            return NotificationDeliveryRecord.of(
                    rule.id(),
                    getChannelName(),
                    webhookUrl,
                    false,
                    0,
                    "Dispatch failure: " + e.getMessage()
            );
        }
    }
}
