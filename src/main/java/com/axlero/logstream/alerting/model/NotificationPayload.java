package com.axlero.logstream.alerting.model;

import java.time.Instant;
import java.util.List;

/**
 * Standard JSON payload formatted for webhook triggers.
 */
public record NotificationPayload(
        String event,
        String ruleId,
        String ruleName,
        String severity,
        int threshold,
        long actualCount,
        int windowMinutes,
        String timestamp,
        String summary,
        List<String> sampleLogs,
        String text
) {
    public static NotificationPayload fromBreach(AlertBreachEvent breach) {
        String summary = String.format(
                "[%s] Alert '%s' breached threshold: %d occurrences detected (threshold: %d) over past %d minutes.",
                breach.severity(),
                breach.ruleName(),
                breach.actualCount(),
                breach.threshold(),
                breach.windowMinutes()
        );

        StringBuilder sb = new StringBuilder();
        sb.append(summary).append("\nQuery: ").append(breach.query()).append("\n");
        if (!breach.sampleLogs().isEmpty()) {
            sb.append("Recent matching logs:\n");
            for (String sample : breach.sampleLogs()) {
                sb.append(" - ").append(sample).append("\n");
            }
        }

        return new NotificationPayload(
                "ALERT_BREACH",
                breach.ruleId(),
                breach.ruleName(),
                breach.severity().name(),
                breach.threshold(),
                breach.actualCount(),
                breach.windowMinutes(),
                breach.breachedAt().toString(),
                summary,
                breach.sampleLogs(),
                sb.toString().trim()
        );
    }

    /**
     * Serializes this payload to JSON string without requiring external Jackson dependencies.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"event\":").append(escapeJson(event)).append(",");
        sb.append("\"ruleId\":").append(escapeJson(ruleId)).append(",");
        sb.append("\"ruleName\":").append(escapeJson(ruleName)).append(",");
        sb.append("\"severity\":").append(escapeJson(severity)).append(",");
        sb.append("\"threshold\":").append(threshold).append(",");
        sb.append("\"actualCount\":").append(actualCount).append(",");
        sb.append("\"windowMinutes\":").append(windowMinutes).append(",");
        sb.append("\"timestamp\":").append(escapeJson(timestamp)).append(",");
        sb.append("\"summary\":").append(escapeJson(summary)).append(",");
        sb.append("\"text\":").append(escapeJson(text)).append(",");
        sb.append("\"sampleLogs\":[");
        for (int i = 0; i < sampleLogs.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(escapeJson(sampleLogs.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
