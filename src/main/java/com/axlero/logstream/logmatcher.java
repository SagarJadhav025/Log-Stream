package com.logstream.search;

import com.logstream.model.LogEntry;


public final class LogMatcher {

    private LogMatcher() {
    }

    public static boolean matches(SearchQuery query, LogEntry entry) {
        if (query.level() != null && !query.level().equals(entry.level())) {
            return false;
        }
        if (query.service() != null && !query.service().equalsIgnoreCase(entry.service())) {
            return false;
        }
        if (!query.terms().isEmpty()) {
            String message = entry.message().toLowerCase();
            for (String term : query.terms()) {
                if (!message.contains(term)) {
                    return false;
                }
            }
        }
        return true;
    }
}