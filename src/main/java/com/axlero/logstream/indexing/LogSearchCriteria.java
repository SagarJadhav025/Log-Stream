
package com.axlero.logstream.indexing;

import java.time.Instant;

public record LogSearchCriteria(
        String message,
        String level,
        String service,
        Instant from,
        Instant to,
        int limit
) {
    public LogSearchCriteria {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be positive"
            );
        }

        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "'from' must not be after 'to'"
            );
        }

        message = normalize(message);
        level = normalize(level);
        service = normalize(service);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
