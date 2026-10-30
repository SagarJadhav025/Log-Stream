package com.axlero.logstream.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.axlero.logstream.Logentry;
import com.axlero.logstream.indexing.LuceneIndexService;

class LuceneLogSearchServiceTest {

    @Test
    void addsAndRetrievesLogPreservingIdAndFields() {
        try (LuceneLogSearchService service = newService()) {
            Logentry input = log("log-42", Instant.parse("2026-10-30T10:15:30Z"),
                    "ERROR", "payments", "Payment authorization failed");

            service.add(input);

            assertEquals(List.of(input), service.search(Searchquery.matchAll()));
        }
    }

    @Test
    void requiresEverySearchTermToMatch() {
        try (LuceneLogSearchService service = newService()) {
            service.add(log("both", Instant.now(), "ERROR", "api", "Database connection timeout"));
            service.add(log("only-database", Instant.now(), "ERROR", "api", "Database is healthy"));
            service.add(log("only-timeout", Instant.now(), "ERROR", "api", "Request timed out"));

            var results = service.search(new Searchquery(
                    List.of("database", "timeout"), null, null, 10
            ));

            assertEquals(2, service.search(new Searchquery(List.of("database"), null, null, 10)).size());
            assertEquals(List.of("both"), results.stream().map(Logentry::id).toList());
        }
    }

    @Test
    void requiresEveryAnalyzedWordWithinEachSearchTerm() {
        try (LuceneLogSearchService service = newService()) {
            service.add(log("both-words", Instant.now(), "INFO", "api",
                    "Database timeout occurred"));
            service.add(log("database-only", Instant.now(), "INFO", "api",
                    "Database connection established"));
            service.add(log("timeout-only", Instant.now(), "INFO", "api",
                    "Request timeout occurred"));

            var results = service.search(new Searchquery(
                    List.of("database timeout"), null, null, 10
            ));

            assertEquals(List.of("both-words"), results.stream().map(Logentry::id).toList());
        }
    }

    @Test
    void filtersByExactLevel() {
        try (LuceneLogSearchService service = newService()) {
            service.add(log("error", Instant.now(), "ERROR", "api", "Request failed"));
            service.add(log("info", Instant.now(), "INFO", "api", "Request failed"));

            var results = service.search(new Searchquery(List.of(), "ERROR", null, 10));

            assertEquals(List.of("error"), results.stream().map(Logentry::id).toList());
        }
    }

    @Test
    void filtersServiceWithoutChangingStoredServiceName() {
        try (LuceneLogSearchService service = newService()) {
            service.add(log("mixed-case", Instant.now(), "INFO", "Payment-API", "Request received"));
            service.add(log("other", Instant.now(), "INFO", "inventory-api", "Request received"));

            var results = service.search(new Searchquery(List.of(), null, "PAYMENT-api", 10));

            assertEquals(1, results.size());
            assertEquals("Payment-API", results.get(0).service());
        }
    }

    @Test
    void filtersByRelativeTimeBoundary() {
        try (LuceneLogSearchService service = newService()) {
            Instant now = Instant.now();
            service.add(log("old", now.minus(Duration.ofMinutes(15)), "INFO", "api", "Heartbeat"));
            service.add(log("recent", now.minus(Duration.ofMinutes(2)), "INFO", "api", "Heartbeat"));

            var results = service.search(new Searchquery(
                    List.of(), null, null, Duration.ofMinutes(5), 10
            ));

            assertEquals(List.of("recent"), results.stream().map(Logentry::id).toList());
        }
    }

    @Test
    void zeroRelativeTimeIncludesOnlyLogsAtOrAfterSearchTime() {
        try (LuceneLogSearchService service = newService()) {
            service.add(log("past", Instant.now().minusSeconds(1), "INFO", "api", "Event"));
            service.add(log("future", Instant.now().plusSeconds(1), "INFO", "api", "Event"));

            var results = service.search(new Searchquery(
                    List.of(), null, null, Duration.ZERO, 10
            ));

            assertEquals(List.of("future"), results.stream().map(Logentry::id).toList());
        }
    }

    @Test
    void newestFirstOrderingUsesSubMillisecondTimestampPrecision() {
        try (LuceneLogSearchService service = newService()) {
            Instant timestamp = Instant.parse("2026-10-30T10:00:00Z");
            service.add(log("later", timestamp.plusNanos(900_000), "INFO", "api", "Event"));
            service.add(log("earlier", timestamp.plusNanos(100_000), "INFO", "api", "Event"));

            var results = service.search(Searchquery.matchAll());

            assertEquals(List.of("later", "earlier"), results.stream().map(Logentry::id).toList());
        }
    }

    @Test
    void returnsResultsNewestFirst() {
        try (LuceneLogSearchService service = newService()) {
            Instant base = Instant.parse("2026-10-30T10:00:00Z");
            service.add(log("old", base, "INFO", "api", "Event"));
            service.add(log("new", base.plusSeconds(1), "INFO", "api", "Event"));

            var results = service.search(Searchquery.matchAll());

            assertEquals(List.of("new", "old"), results.stream().map(Logentry::id).toList());
        }
    }

    @Test
    void ordersEqualTimestampsByMostRecentlyAddedFirst() {
        try (LuceneLogSearchService service = newService()) {
            Instant timestamp = Instant.parse("2026-10-30T10:00:00Z");
            service.add(log("first", timestamp, "INFO", "api", "Event"));
            service.add(log("second", timestamp, "INFO", "api", "Event"));

            var results = service.search(Searchquery.matchAll());

            assertEquals(List.of("second", "first"), results.stream().map(Logentry::id).toList());
        }
    }

    @Test
    void emptyQueryMatchesAllIndexedLogs() {
        try (LuceneLogSearchService service = newService()) {
            service.add(log("one", Instant.now(), "INFO", "api", "First"));
            service.add(log("two", Instant.now(), "ERROR", "worker", "Second"));

            assertEquals(2, service.search(Searchquery.matchAll()).size());
        }
    }

    @Test
    void appliesDefaultAndMaximumResultLimits() {
        try (LuceneLogSearchService service = newService()) {
            Instant base = Instant.parse("2026-10-30T10:00:00Z");
            for (int i = 0; i < 1_005; i++) {
                service.add(log("log-" + i, base.plusMillis(i), "INFO", "api", "Event"));
            }

            assertEquals(Searchquery.DEFAULT_LIMIT,
                    service.search(new Searchquery(List.of(), null, null, 0)).size());
            assertEquals(Searchquery.MAX_LIMIT,
                    service.search(new Searchquery(List.of(), null, null, 5_000)).size());
        }
    }

    @Test
    void returnsEmptyListWhenNothingMatches() {
        try (LuceneLogSearchService service = newService()) {
            service.add(log("log", Instant.now(), "INFO", "api", "Request received"));

            assertTrue(service.search(new Searchquery(List.of("database"), null, null, 10)).isEmpty());
        }
    }

    @Test
    void rejectsNullEntryAndQuery() {
        try (LuceneLogSearchService service = newService()) {
            assertThrows(NullPointerException.class, () -> service.add(null));
            assertThrows(NullPointerException.class, () -> service.search(null));
        }
    }

    @Test
    void rejectsNegativeRelativeTime() {
        try (LuceneLogSearchService service = newService()) {
            Searchquery invalid = new Searchquery(List.of(), null, null, Duration.ofSeconds(-1), 10);

            assertThrows(IllegalArgumentException.class, () -> service.search(invalid));
        }
    }

    private static Logentry log(
            String id,
            Instant timestamp,
            String level,
            String service,
            String message
    ) {
        return new Logentry(id, timestamp, level, service, message);
    }

    private static LuceneLogSearchService newService() {
        try {
            return new LuceneLogSearchService(new LuceneIndexService());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
