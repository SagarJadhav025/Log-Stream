package com.axlero.logstream.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueryParser {

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

        Matcher matcher = TOKEN.matcher(input);

        while (matcher.find()) {
            String phrase = matcher.group(1);

            if (phrase != null) {
                if (!phrase.isBlank()) {
                    terms.add(phrase.trim().toLowerCase(Locale.ROOT));
                }
                continue;
            }

            String token = matcher.group(2);
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

    private static Duration parseSince(String value) {
        try {
            String input = value.toLowerCase(Locale.ROOT);

            if (input.endsWith("ms")) {
                return Duration.ofMillis(
                        Long.parseLong(input.substring(0, input.length() - 2)));
            }

            if (input.endsWith("s")) {
                return Duration.ofSeconds(
                        Long.parseLong(input.substring(0, input.length() - 1)));
            }

            if (input.endsWith("m")) {
                return Duration.ofMinutes(
                        Long.parseLong(input.substring(0, input.length() - 1)));
            }

            if (input.endsWith("h")) {
                return Duration.ofHours(
                        Long.parseLong(input.substring(0, input.length() - 1)));
            }

            if (input.endsWith("d")) {
                return Duration.ofDays(
                        Long.parseLong(input.substring(0, input.length() - 1)));
            }

            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseLimit(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
