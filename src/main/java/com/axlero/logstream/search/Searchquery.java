package com.axlero.logstream.search;

import java.time.Duration;
import java.util.List;


public record Searchquery(List<String> terms, String level, String service, Duration since, int limit) {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 1000;

    public Searchquery {
        terms = terms == null ? List.of() : List.copyOf(terms);
        level = (level == null || level.isBlank()) ? null : level.trim().toUpperCase();
        service = (service == null || service.isBlank()) ? null : service.trim().toLowerCase();
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        limit = Math.min(limit, MAX_LIMIT);
    }

    /** same as before the time filter existed (since = any time) */
    public Searchquery(List<String> terms, String level, String service, int limit) {
        this(terms, level, service, null, limit);
    }

    public static Searchquery matchAll() {
        return new Searchquery(List.of(), null, null, null, DEFAULT_LIMIT);
    }

    /** copy of this query with a different limit (keeps every other filter) */
    public Searchquery withLimit(int newLimit) {
        return new Searchquery(terms, level, service, since, newLimit);
    }
}