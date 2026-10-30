package com.axlero.logstream.search;

import java.util.Locale;

import com.axlero.logstream.Logentry;

public final class logmatcher {

    private logmatcher() {
    }

    public static boolean matches(Searchquery query, Logentry entry) {
        if (query.level() != null && !query.level().equals(entry.level())) {
            return false;
        }
        if (query.service() != null && !query.service().equalsIgnoreCase(entry.service())) {
            return false;
        }
        if (!query.terms().isEmpty()) {
            String message = entry.message().toLowerCase(Locale.ROOT);
            for (String term : query.terms()) {
                if (!message.contains(term)) {
                    return false;
                }
            }
        }
        return true;
    }
}
