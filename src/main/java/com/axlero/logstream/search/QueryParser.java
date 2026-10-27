package com.axlero.logstream.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.axlero.logstream.search.Searchquery;


public final class QueryParser {

    // either a "quoted phrase" or a run of non-space characters
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    private QueryParser() {
    }

    public static Searchquery parse(String input) {
        if (input == null || input.isBlank()) {
            return Searchquery.matchAll();
        }

        List<String> terms = new ArrayList<>();
        String level = null;
        String service = null;
        Duration since = null;
        int limit = Searchquery.DEFAULT_LIMIT;

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
            } else if (lower.startsWith("since:")) {
                since = parseSince(token.substring(6)); // bad value -> null -> ignored
            } else if (lower.startsWith("limit:")) {
                limit = parseLimit(token.substring(6), limit);
            } else {
                terms.add(lower);
            }
        }
        return new Searchquery(terms, level, service, since, limit);
    }

    private static Duration parseSince(String text) {
        if (text == null || text.length() < 2) {
            return null;
        }
        try {
            long n = Long.parseLong(text.substring(0, text.length() - 1));
            if (n <= 0) {
                return null;
            }
            switch (Character.toLowerCase(text.charAt(text.length() - 1))) {
                case 's': return Duration.ofSeconds(n);
                case 'm': return Duration.ofMinutes(n);
                case 'h': return Duration.ofHours(n);
                case 'd': return Duration.ofDays(n);
                default:  return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }
    private static int parseLimit(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback; // "limit:abc" is ignored instead of crashing
        }
    }
}