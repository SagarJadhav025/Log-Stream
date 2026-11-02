package com.axlero.logstream.alerting.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record documenting a notification dispatch attempt.
 */
public record NotificationDeliveryRecord(
        String id,
        String ruleId,
        String channel,
        String destination,
        Instant timestamp,
        boolean success,
        int statusCode,
        String message
) {
    public static NotificationDeliveryRecord of(String ruleId, String channel, String destination, boolean success, int statusCode, String message) {
        return new NotificationDeliveryRecord(
                UUID.randomUUID().toString(),
                ruleId,
                channel,
                destination,
                Instant.now(),
                success,
                statusCode,
                message
        );
    }
}
