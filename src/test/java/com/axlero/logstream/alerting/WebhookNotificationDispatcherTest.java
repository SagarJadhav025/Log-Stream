package com.axlero.logstream.alerting;

import com.axlero.logstream.alerting.dispatcher.WebhookNotificationDispatcher;
import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.AlertSeverity;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebhookNotificationDispatcherTest {

    private WebhookNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new WebhookNotificationDispatcher();
    }

    @Test
    void supportsRulesWithValidWebhookUrl() {
        AlertRule withUrl = AlertRule.of("r1", "Test", "level:ERROR", 5, 5, "http://localhost:8080/sink");
        AlertRule withoutUrl = new AlertRule("r2", "Test2", "level:ERROR", 5, 5, 5, null, null, AlertSeverity.CRITICAL, true);
        AlertRule blankUrl = new AlertRule("r3", "Test3", "level:ERROR", 5, 5, 5, "   ", null, AlertSeverity.CRITICAL, true);

        assertTrue(dispatcher.supports(withUrl));
        assertFalse(dispatcher.supports(withoutUrl));
        assertFalse(dispatcher.supports(blankUrl));
    }

    @Test
    void dispatchesSimulatedWebhookSuccessfully() {
        AlertRule rule = AlertRule.of("r1", "DB Failure", "level:ERROR", 5, 5, "simulated://test-webhook");
        AlertBreachEvent breach = new AlertBreachEvent(
                rule.id(),
                rule.name(),
                rule.query(),
                rule.threshold(),
                12,
                rule.windowMinutes(),
                Instant.now(),
                rule.severity(),
                List.of("[2026-11-02] [ERROR] payment-service - DB timeout")
        );

        NotificationDeliveryRecord record = dispatcher.dispatch(breach, rule);

        assertNotNull(record);
        assertTrue(record.success());
        assertEquals(200, record.statusCode());
        assertEquals("WEBHOOK", record.channel());
        assertEquals("simulated://test-webhook", record.destination());
    }

    @Test
    void handlesInvalidHostGracefullyWithoutThrowing() {
        AlertRule rule = AlertRule.of("r1", "DB Failure", "level:ERROR", 5, 5, "http://non-existent-invalid-host-99999.test/webhook");
        AlertBreachEvent breach = new AlertBreachEvent(
                rule.id(),
                rule.name(),
                rule.query(),
                rule.threshold(),
                10,
                rule.windowMinutes(),
                Instant.now(),
                rule.severity(),
                List.of()
        );

        NotificationDeliveryRecord record = dispatcher.dispatch(breach, rule);

        assertNotNull(record);
        assertFalse(record.success());
        assertEquals("WEBHOOK", record.channel());
        assertTrue(record.message().contains("Dispatch failure"));
    }
}
