package com.logstream.search;

import java.util.List;


public record Searchquery(List<String> terms, String level, String service, int limit) {

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

    public static Searchquery matchAll() {
        return new Searchquery(List.of(), null, null, DEFAULT_LIMIT);
    }
}