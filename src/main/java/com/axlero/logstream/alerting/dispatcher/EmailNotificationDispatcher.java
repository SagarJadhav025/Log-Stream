package com.axlero.logstream.alerting.dispatcher;

import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dispatches simulated email alerts when thresholds are breached.
 */
@Component
public class EmailNotificationDispatcher implements NotificationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationDispatcher.class);

    @Override
    public boolean supports(AlertRule rule) {
        return rule.notifyEmail() != null && !rule.notifyEmail().isBlank();
    }

    @Override
    public String getChannelName() {
        return "EMAIL";
    }

    @Override
    public NotificationDeliveryRecord dispatch(AlertBreachEvent breach, AlertRule rule) {
        String recipient = rule.notifyEmail().trim();

        String subject = String.format("[%s ALERT] %s - Threshold Breached",
                breach.severity(), breach.ruleName());

        StringBuilder body = new StringBuilder();
        body.append("From: alerts@logstream.internal\n");
        body.append("To: ").append(recipient).append("\n");
        body.append("Subject: ").append(subject).append("\n");
        body.append("Timestamp: ").append(breach.breachedAt()).append("\n\n");
        body.append("Alert Incident Details:\n");
        body.append("--------------------------------------------------\n");
        body.append("Rule:         ").append(breach.ruleName()).append(" (ID: ").append(breach.ruleId()).append(")\n");
        body.append("Severity:     ").append(breach.severity()).append("\n");
        body.append("Query:        ").append(breach.query()).append("\n");
        body.append("Threshold:    ").append(breach.threshold()).append(" events\n");
        body.append("Actual Count: ").append(breach.actualCount()).append(" events in the last ")
                .append(breach.windowMinutes()).append(" minutes\n");

        if (!breach.sampleLogs().isEmpty()) {
            body.append("\nSample Matching Logs:\n");
            for (int i = 0; i < breach.sampleLogs().size(); i++) {
                body.append(" [").append(i + 1).append("] ").append(breach.sampleLogs().get(i)).append("\n");
            }
        }
        body.append("--------------------------------------------------\n");

        logger.info("\n=== [SIMULATED EMAIL DISPATCH] ===\n{}\n==================================", body);

        return NotificationDeliveryRecord.of(
                rule.id(),
                getChannelName(),
                recipient,
                true,
                250,
                "Simulated email delivered to " + recipient
        );
    }
}
