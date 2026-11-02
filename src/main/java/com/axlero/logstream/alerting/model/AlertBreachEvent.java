package com.axlero.logstream.alerting.model;

import java.time.Instant;
import java.util.List;

/**
 * Event captured when a rule query breaches its threshold.
 */
public record AlertBreachEvent(
        String ruleId,
        String ruleName,
        String query,
        int threshold,
        long actualCount,
        int windowMinutes,
        Instant breachedAt,
        AlertSeverity severity,
        List<String> sampleLogs
) {
    public AlertBreachEvent {
        if (breachedAt == null) {
            breachedAt = Instant.now();
        }
        sampleLogs = sampleLogs == null ? List.of() : List.copyOf(sampleLogs);
    }
}
