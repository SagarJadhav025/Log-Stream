package com.axlero.logstream.pipeline;

import java.time.Instant;

public class LogParser {
    public static LogEntry parse(String line) {
        String[] p = line.split("\\s+", 5);
        if (p.length < 5) throw new IllegalArgumentException("Malformed log: " + line);
        return new LogEntry(Instant.parse(p[0]).toEpochMilli(),
                p[1].toUpperCase(), p[2], Long.parseLong(p[3]), p[4]);
    }
}
