package com.axlero.logstream.alerting;

import com.axlero.logstream.alerting.dispatcher.NotificationDispatcher;
import com.axlero.logstream.alerting.engine.RuleEngine;
import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.AlertSeverity;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import com.axlero.logstream.alerting.service.AlertManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AlertManagerServiceTest {

    private AlertManagerService service;
    private List<NotificationDeliveryRecord> testDispatched;

    @BeforeEach
    void setUp() {
        testDispatched = new ArrayList<>();

        RuleEngine mockEngine = new RuleEngine(null) {
            @Override
            public Optional<AlertBreachEvent> evaluate(AlertRule rule) {
                if ("trigger-rule".equals(rule.id())) {
                    return Optional.of(new AlertBreachEvent(
                            rule.id(),
                            rule.name(),
                            rule.query(),
                            rule.threshold(),
                            20,
                            rule.windowMinutes(),
                            Instant.now(),
                            rule.severity(),
                            List.of("mock log line")
                    ));
                }
                return Optional.empty();
            }
        };

        NotificationDispatcher testDispatcher = new NotificationDispatcher() {
            @Override
            public boolean supports(AlertRule rule) {
                return rule.webhookUrl() != null;
            }

            @Override
            public NotificationDeliveryRecord dispatch(AlertBreachEvent breach, AlertRule rule) {
                NotificationDeliveryRecord rec = NotificationDeliveryRecord.of(
                        rule.id(), "WEBHOOK", rule.webhookUrl(), true, 200, "Delivered"
                );
                testDispatched.add(rec);
                return rec;
            }

            @Override
            public String getChannelName() {
                return "WEBHOOK";
            }
        };

        service = new AlertManagerService(mockEngine, List.of(testDispatcher));
    }

    @Test
    void savesAndRetrievesRules() {
        AlertRule rule = AlertRule.of("r100", "Custom Rule", "level:WARN", 2, 5, "simulated://url");
        service.saveRule(rule);

        Optional<AlertRule> found = service.getRule("r100");
        assertTrue(found.isPresent());
        assertEquals("Custom Rule", found.get().name());

        assertTrue(service.deleteRule("r100"));
        assertTrue(service.getRule("r100").isEmpty());
    }

    @Test
    void evaluatesRulesAndEnforcesCooldown() {
        AlertRule rule = AlertRule.of("trigger-rule", "Spike Alert", "level:ERROR", 5, 5, "simulated://url");
        service.saveRule(rule);

        // First evaluation: breach should be detected and dispatched
        List<AlertBreachEvent> breaches1 = service.evaluateAll();
        assertEquals(1, breaches1.size());
        assertEquals(1, testDispatched.size());
        assertEquals(1, service.getDeliveryHistory().size());

        // Second evaluation immediately after: rule is in cooldown, so no breach or dispatch occurs
        List<AlertBreachEvent> breaches2 = service.evaluateAll();
        assertEquals(0, breaches2.size());
        assertEquals(1, testDispatched.size());

        // But manual evaluation with bypassCooldown=true executes
        Optional<AlertBreachEvent> manualBreach = service.evaluateRule("trigger-rule", true);
        assertTrue(manualBreach.isPresent());
        assertEquals(2, testDispatched.size());
    }
}
