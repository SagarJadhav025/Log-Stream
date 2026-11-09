package com.axlero.logstream.search;

import org.apache.lucene.queryparser.classic.QueryParser;
import java.util.regex.Pattern;

public final class LuceneQuerySanitizer {

    public static final int MAX_QUERY_LENGTH = 300;
    private static final Pattern LEADING_WILDCARD_PATTERN = Pattern.compile("(^|\\s)[*?]+");
    private static final Pattern INTERNAL_FIELD_PATTERN = Pattern.compile("(?i)\\b(id|timestamp_epoch_millis|timestamp_epoch_seconds|timestamp_nano|timestamp_stored):");

    private LuceneQuerySanitizer() {}

    /**
     * Sanitizes free-text user search strings by escaping all Lucene reserved characters.
     */
    public static String sanitize(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        String trimmed = rawQuery.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }

        // 1. Remove leading wildcards that defeat inverted index lookups
        trimmed = LEADING_WILDCARD_PATTERN.matcher(trimmed).replaceAll("$1");

        // 2. Prevent field injection into unauthorized internal fields
        trimmed = INTERNAL_FIELD_PATTERN.matcher(trimmed).replaceAll("");

        // 3. Escape Lucene reserved characters
        return QueryParser.escape(trimmed.trim());
    }

    /**
     * Sanitizes structured Lucene syntax queries (e.g. alert rule queries) while preserving
     * valid field filters (e.g., 'level:ERROR') and boolean operators ('AND', 'OR', 'NOT').
     */
    public static String sanitizeLuceneSyntax(String queryText) {
        if (queryText == null || queryText.isBlank() || queryText.trim().equals("*:*")) {
            return "*:*";
        }

        String trimmed = queryText.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }

        // 1. Block dangerous leading wildcards like "*error" or "?error"
        trimmed = LEADING_WILDCARD_PATTERN.matcher(trimmed).replaceAll("$1");

        // 2. Strip internal fields that users must not directly query
        trimmed = INTERNAL_FIELD_PATTERN.matcher(trimmed).replaceAll("");

        // 3. Balance unpaired double quotes to prevent ParseException crashes
        long quoteCount = trimmed.chars().filter(ch -> ch == '"').count();
        if (quoteCount % 2 != 0) {
            trimmed = trimmed.replace("\"", "");
        }

        return trimmed.trim().isBlank() ? "*:*" : trimmed.trim();
    }
}
