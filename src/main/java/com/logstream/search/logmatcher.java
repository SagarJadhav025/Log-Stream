package com.logstream.search;

import java.time.Instant;

import com.axlero.logstream.Logentry;

public final class logmatcher {

    private logmatcher() {
    }

    public static boolean matches(Searchquery query, Logentry entry, Instant now) {
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