package com.axlero.logstream.alerting.service;

import com.axlero.logstream.alerting.dispatcher.NotificationDispatcher;
import com.axlero.logstream.alerting.engine.RuleEngine;
import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.AlertSeverity;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Coordinates alert rule evaluation, cooldown enforcement, and notification dispatching.
 */
@Service
public class AlertManagerService {

    private static final Logger logger = LoggerFactory.getLogger(AlertManagerService.class);
    private static final int MAX_AUDIT_HISTORY = 200;

    private final RuleEngine ruleEngine;
    private final List<NotificationDispatcher> dispatchers;

    private final Map<String, AlertRule> rules = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFired = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<NotificationDeliveryRecord> deliveryHistory = new ConcurrentLinkedDeque<>();

    public AlertManagerService(RuleEngine ruleEngine, List<NotificationDispatcher> dispatchers) {
        this.ruleEngine = ruleEngine;
        this.dispatchers = dispatchers != null ? dispatchers : List.of();
    }

    @PostConstruct
    void initDefaultRules() {
        // Seed initial default observability rules matching project specs
        saveRule(new AlertRule(
                "rule-error-spike",
                "High Error Spike",
                "level:ERROR",
                10,
                5,
                5,
                "simulated://slack-webhook/alerts",
                "ops-alerts@axlero.com",
                AlertSeverity.CRITICAL,
                true
        ));

        saveRule(new AlertRule(
                "rule-db-timeout",
                "Database Timeout Spike",
                "message:timeout AND level:ERROR",
                3,
                10,
                5,
                "simulated://pagerduty-webhook/db",
                "database-team@axlero.com",
                AlertSeverity.CRITICAL,
                true
        ));
    }

    public AlertRule saveRule(AlertRule rule) {
        rules.put(rule.id(), rule);
        logger.info("Saved alert rule '{}' (id: {})", rule.name(), rule.id());
        return rule;
    }

    public Optional<AlertRule> getRule(String ruleId) {
        return Optional.ofNullable(rules.get(ruleId));
    }

    public boolean deleteRule(String ruleId) {
        lastFired.remove(ruleId);
        return rules.remove(ruleId) != null;
    }

    public Collection<AlertRule> getAllRules() {
        return Collections.unmodifiableCollection(rules.values());
    }

    /**
     * Evaluates all enabled alert rules.
     *
     * @return List of generated breach events
     */
    public List<AlertBreachEvent> evaluateAll() {
        List<AlertBreachEvent> breaches = new ArrayList<>();

        for (AlertRule rule : rules.values()) {
            if (!rule.enabled()) {
                continue;
            }

            if (isInCooldown(rule)) {
                logger.debug("Rule '{}' is currently in cooldown; skipping evaluation.", rule.name());
                continue;
            }

            Optional<AlertBreachEvent> breachOpt = ruleEngine.evaluate(rule);
            if (breachOpt.isPresent()) {
                AlertBreachEvent breach = breachOpt.get();
                breaches.add(breach);
                lastFired.put(rule.id(), Instant.now());
                dispatchNotifications(breach, rule);
            }
        }

        return breaches;
    }

    /**
     * Evaluates a specific rule immediately, bypassing cooldown for manual test/trigger.
     */
    public Optional<AlertBreachEvent> evaluateRule(String ruleId, boolean bypassCooldown) {
        AlertRule rule = rules.get(ruleId);
        if (rule == null) {
            return Optional.empty();
        }

        if (!bypassCooldown && isInCooldown(rule)) {
            logger.info("Rule '{}' is in cooldown.", rule.name());
            return Optional.empty();
        }

        Optional<AlertBreachEvent> breachOpt = ruleEngine.evaluate(rule);
        breachOpt.ifPresent(breach -> {
            lastFired.put(rule.id(), Instant.now());
            dispatchNotifications(breach, rule);
        });

        return breachOpt;
    }

    /**
     * Dispatches notifications across all supported dispatchers.
     */
    public List<NotificationDeliveryRecord> dispatchNotifications(AlertBreachEvent breach, AlertRule rule) {
        List<NotificationDeliveryRecord> records = new ArrayList<>();

        for (NotificationDispatcher dispatcher : dispatchers) {
            if (dispatcher.supports(rule)) {
                NotificationDeliveryRecord record = dispatcher.dispatch(breach, rule);
                recordDelivery(record);
                records.add(record);
            }
        }

        return records;
    }

    /**
     * Tests a webhook destination with a mock breach event.
     */
    public NotificationDeliveryRecord testWebhook(String webhookUrl) {
        AlertRule testRule = new AlertRule(
                "test-rule",
                "Test Alert Notification",
                "level:ERROR",
                1,
                5,
                0,
                webhookUrl,
                null,
                AlertSeverity.INFO,
                true
        );

        AlertBreachEvent testBreach = new AlertBreachEvent(
                testRule.id(),
                testRule.name(),
                testRule.query(),
                testRule.threshold(),
                42,
                5,
                Instant.now(),
                testRule.severity(),
                List.of(
                        "[INFO] [billing-api] Test connectivity log event",
                        "[ERROR] [auth-service] Simulated error for webhook testing"
                )
        );

        for (NotificationDispatcher dispatcher : dispatchers) {
            if ("WEBHOOK".equalsIgnoreCase(dispatcher.getChannelName())) {
                NotificationDeliveryRecord record = dispatcher.dispatch(testBreach, testRule);
                recordDelivery(record);
                return record;
            }
        }

        return NotificationDeliveryRecord.of(
                "test-rule", "WEBHOOK", webhookUrl, false, 500, "No webhook dispatcher found"
        );
    }

    private boolean isInCooldown(AlertRule rule) {
        Instant fired = lastFired.get(rule.id());
        if (fired == null) {
            return false;
        }
        Instant cooldownEnd = fired.plus(Duration.ofMinutes(rule.cooldownMinutes()));
        return Instant.now().isBefore(cooldownEnd);
    }

    private void recordDelivery(NotificationDeliveryRecord record) {
        deliveryHistory.addFirst(record);
        while (deliveryHistory.size() > MAX_AUDIT_HISTORY) {
            deliveryHistory.removeLast();
        }
    }

    public List<NotificationDeliveryRecord> getDeliveryHistory() {
        return new ArrayList<>(deliveryHistory);
    }

    public void clearDeliveryHistory() {
        deliveryHistory.clear();
    }
}
