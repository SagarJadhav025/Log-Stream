package com.axlero.logstream;

import java.time.Instant;
import java.util.Locale;


public record Logentry(String id, Instant timestamp, String level, String service, String message) {

    public Logentry {
        
        level = level == null ? "INFO" : level.trim().toUpperCase(Locale.ROOT);
        service = service == null ? "unknown" : service.trim();
        message = message == null ? "" : message;
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
