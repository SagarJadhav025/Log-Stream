package com.axlero.logstream;

import java.time.Instant;


public record Logentry(String id, Instant timestamp, String level, String service, String message) {

    public Logentry {
        
        level = level == null ? "INFO" : level.trim().toUpperCase();
        service = service == null ? "unknown" : service.trim();
        message = message == null ? "" : message;
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}