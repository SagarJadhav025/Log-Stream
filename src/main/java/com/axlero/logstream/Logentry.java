package com.logstream.model;

import java.time.Instant;


public record LogEntry(String id, Instant timestamp, String level, String service, String message) {

    public LogEntry {
        // keep the level in one format so filters like level:error work
        level = level == null ? "INFO" : level.trim().toUpperCase();
        service = service == null ? "unknown" : service.trim();
        message = message == null ? "" : message;
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}