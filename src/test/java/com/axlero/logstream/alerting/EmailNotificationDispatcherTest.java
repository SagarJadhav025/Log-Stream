package com.axlero.logstream.alerting;

import com.axlero.logstream.alerting.dispatcher.EmailNotificationDispatcher;
import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.AlertSeverity;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailNotificationDispatcherTest {

    private EmailNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new EmailNotificationDispatcher();
    }

    @Test
    void supportsRulesWithConfiguredEmail() {
        AlertRule withEmail = new AlertRule("r1", "Test", "level:ERROR", 5, 5, 5, null, "ops@test.com", AlertSeverity.CRITICAL, true);
        AlertRule withoutEmail = AlertRule.of("r2", "Test", "level:ERROR", 5, 5, "http://localhost/sink");

        assertTrue(dispatcher.supports(withEmail));
        assertFalse(dispatcher.supports(withoutEmail));
    }

    @Test
    void dispatchesSimulatedEmailSuccessfully() {
        AlertRule rule = new AlertRule("r1", "Critical Alert", "level:ERROR", 5, 5, 5, null, "admin@axlero.com", AlertSeverity.CRITICAL, true);
        AlertBreachEvent breach = new AlertBreachEvent(
                rule.id(),
                rule.name(),
                rule.query(),
                rule.threshold(),
                15,
                rule.windowMinutes(),
                Instant.now(),
                rule.severity(),
                List.of("[ERROR] billing-api - Connection refused")
        );

        NotificationDeliveryRecord record = dispatcher.dispatch(breach, rule);

        assertNotNull(record);
        assertTrue(record.success());
        assertEquals("EMAIL", record.channel());
        assertEquals("admin@axlero.com", record.destination());
        assertEquals(250, record.statusCode());
    }
}
