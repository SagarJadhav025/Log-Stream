package com.axlero.logstream.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertNotifier {
    private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);

    private final WebhookDispatcher webhook;

    public AlertNotifier(WebhookDispatcher webhook) {
        this.webhook = webhook;
    }

    /** Called by the rule engine when a threshold is breached. Returns true if the webhook was delivered. */
    public boolean dispatch(AlertEvent alert) {
        log.warn("ALERT '{}': {} matches (threshold {}) for query [{}]",
                alert.ruleName(), alert.count(), alert.threshold(), alert.query());
        log.info("[simulated email] To: oncall@logstream.local | Subject: ALERT {} | {} matches in window",
                alert.ruleName(), alert.count());
        return webhook.send(alert.toJson());
    }
}
