
package com.axlero.logstream.indexing;

import java.time.Instant;
import java.util.Objects;

public record LogEvent(
        Instant timestamp,
        String level,
        String service,
        String message,
        String id
) {
    public LogEvent {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(message, "message must not be null");

        if (level.isBlank() || service.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException(
                    "level, service, and message must not be blank"
            );
        }
    }

    /** Retains the original constructor for existing callers. */
    public LogEvent(
            Instant timestamp,
            String level,
            String service,
            String message
    ) {
        this(timestamp, level, service, message, null);
    }
}
