package com.axlero.logstream.pipeline;

public record LogEntry(long timestamp, String level, String service,
                       long responseTime, String message) {}
