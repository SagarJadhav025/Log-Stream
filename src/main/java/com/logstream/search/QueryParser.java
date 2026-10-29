package com.logstream.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueryParser {

    // Either a "quoted phrase" or a run of non-space characters.
    private static final Pattern TOKEN =
            Pattern.compile("\"([^\"]*)\"|(\\S+)");

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
                    terms.add(phrase.trim().toLowerCase(Locale.ROOT));
                }
                continue;
            }

            String token = m.group(2);
            String lower = token.toLowerCase(Locale.ROOT);

            if (lower.startsWith("level:") && token.length() > 6) {
                level = token.substring(6);
            } else if (lower.startsWith("service:") && token.length() > 8) {
                service = token.substring(8);
            } else if (lower.startsWith("since:")) {
                since = parseSince(token.substring(6));
            } else if (lower.startsWith("limit:")) {
                limit = parseLimit(token.substring(6), limit);
            } else {
                terms.add(lower);
            }
        }

        return new Searchquery(terms, level, service, since, limit);
    }

    /**
     * Parses durations such as 500ms, 30s, 10m, 2h and 7d.
     * Invalid or unsupported values return null.
     */
    private static Duration parseSince(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String value = text.trim().toLowerCase(Locale.ROOT);

        try {
            if (value.endsWith("ms")) {
                return Duration.ofMillis(
                        Long.parseLong(value.substring(0, value.length() - 2)));
            }
            if (value.endsWith("s")) {
                return Duration.ofSeconds(
                        Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("m")) {
                return Duration.ofMinutes(
                        Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("h")) {
                return Duration.ofHours(
                        Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("d")) {
                return Duration.ofDays(
                        Long.parseLong(value.substring(0, value.length() - 1)));
            }
        } catch (NumberFormatException ex) {
            return null;
        }

        return null;
    }

    private static int parseLimit(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
