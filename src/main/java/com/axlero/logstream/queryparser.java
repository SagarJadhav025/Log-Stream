package com.logstream.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the text a user types into a SearchQuery.
 *
 * Examples:
 *   timeout                          -> message contains "timeout"
 *   level:ERROR                      -> only ERROR logs
 *   level:error service:auth login   -> ERROR logs from auth that contain "login"
 *   "connection refused" limit:20    -> exact phrase, at most 20 results
 *
 * Anything that is not a level:/service:/limit: filter is a search word,
 * and every search word has to match (AND).
 */
public final class QueryParser {

    // either a "quoted phrase" or a run of non-space characters
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    private QueryParser() {
    }

    public static SearchQuery parse(String input) {
        if (input == null || input.isBlank()) {
            return SearchQuery.matchAll();
        }

        List<String> terms = new ArrayList<>();
        String level = null;
        String service = null;
        int limit = SearchQuery.DEFAULT_LIMIT;

        Matcher m = TOKEN.matcher(input);
        while (m.find()) {
            String phrase = m.group(1);
            if (phrase != null) {
                if (!phrase.isBlank()) {
                    terms.add(phrase.trim().toLowerCase());
                }
                continue;
            }

            String token = m.group(2);
            String lower = token.toLowerCase();
            if (lower.startsWith("level:") && token.length() > 6) {
                level = token.substring(6);
            } else if (lower.startsWith("service:") && token.length() > 8) {
                service = token.substring(8);
            } else if (lower.startsWith("limit:")) {
                limit = parseLimit(token.substring(6), limit);
            } else {
                terms.add(lower);
            }
        }
        return new SearchQuery(terms, level, service, limit);
    }

    private static int parseLimit(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback; // "limit:abc" is ignored instead of crashing
        }
    }
}