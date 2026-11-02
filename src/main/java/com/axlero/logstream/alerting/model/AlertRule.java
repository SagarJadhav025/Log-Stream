package com.axlero.logstream.alerting.model;

import java.util.Objects;

/**
 * Definition of an alert rule evaluated against indexed logs.
 */
public record AlertRule(
        String id,
        String name,
        String query,
        int threshold,
        int windowMinutes,
        int cooldownMinutes,
        String webhookUrl,
        String notifyEmail,
        AlertSeverity severity,
        boolean enabled
) {
    public AlertRule {
        Objects.requireNonNull(id, "Rule id must not be null");
        Objects.requireNonNull(name, "Rule name must not be null");
        query = (query == null || query.isBlank()) ? "*:*" : query.trim();
        if (threshold <= 0) {
            threshold = 1;
        }
        if (windowMinutes <= 0) {
            windowMinutes = 5;
        }
        if (cooldownMinutes < 0) {
            cooldownMinutes = 5;
        }
        if (severity == null) {
            severity = AlertSeverity.CRITICAL;
        }
    }

    public static AlertRule of(String id, String name, String query, int threshold, int windowMinutes, String webhookUrl) {
        return new AlertRule(id, name, query, threshold, windowMinutes, 5, webhookUrl, null, AlertSeverity.CRITICAL, true);
    }
}
