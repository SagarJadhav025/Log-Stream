package com.axlero.logstream.alerting;

import java.time.Instant;

public record AlertEvent(String ruleName, String query, long count, long threshold, Instant time) {

    public String toJson() {
        return "{\"ruleName\":\"" + esc(ruleName) + "\","
                + "\"query\":\"" + esc(query) + "\","
                + "\"count\":" + count + ","
                + "\"threshold\":" + threshold + ","
                + "\"time\":\"" + time + "\"}";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
