
package com.axlero.logstream.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class LuceneIndexServiceTests {

    @Test
    void indexesAndFindsLogMessage() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            LogEvent event = new LogEvent(
                    Instant.parse("2026-10-26T10:00:00Z"),
                    "ERROR",
                    "payment-service",
                    "Database connection failed"
            );

            service.index(event);

            List<LogEvent> results =
                    service.searchMessage("database", 10);

            assertEquals(1, results.size());
            assertEquals("payment-service", results.get(0).service());
            assertEquals("ERROR", results.get(0).level());
            assertEquals(
                    "Database connection failed",
                    results.get(0).message()
            );
        }
    }

    @Test
    void returnsEmptyListWhenNothingMatches() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            service.index(new LogEvent(
                    Instant.parse("2026-10-26T10:00:00Z"),
                    "INFO",
                    "order-service",
                    "Order created successfully"
            ));

            List<LogEvent> results =
                    service.searchMessage("database", 10);

            assertEquals(List.of(), results);
        }
    }

    @Test
    void respectsResultLimit() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            Instant timestamp = Instant.parse("2026-10-26T10:00:00Z");

            service.index(new LogEvent(
                    timestamp, "ERROR", "service-a", "Database unavailable"
            ));
            service.index(new LogEvent(
                    timestamp, "ERROR", "service-b", "Database timeout"
            ));

            List<LogEvent> results =
                    service.searchMessage("database", 1);

            assertEquals(1, results.size());
        }
    }

    @Test
    void rejectsBlankSearchText() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.searchMessage(" ", 10)
            );
        }
    }

    @Test
    void rejectsNonPositiveResultLimit() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.searchMessage("database", 0)
            );
        }
    }

    @Test
    void filtersByExactLogLevel() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:00:00Z"),
                    "ERROR", "payment", "Payment failed"
            ));
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:01:00Z"),
                    "INFO", "payment", "Payment completed"
            ));

            var results = service.search(new LogSearchCriteria(
                    null, "ERROR", null, null, null, 10
            ));

            assertEquals(1, results.size());
            assertEquals("ERROR", results.get(0).level());
        }
    }

    @Test
    void filtersByExactService() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:00:00Z"),
                    "INFO", "payment", "Request received"
            ));
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:01:00Z"),
                    "INFO", "inventory", "Request received"
            ));

            var results = service.search(new LogSearchCriteria(
                    null, null, "payment", null, null, 10
            ));

            assertEquals(1, results.size());
            assertEquals("payment", results.get(0).service());
        }
    }

    @Test
    void filtersByInclusiveTimestampRange() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            Instant start = Instant.parse("2026-01-01T10:00:00Z");
            Instant middle = Instant.parse("2026-01-01T10:01:00Z");
            Instant end = Instant.parse("2026-01-01T10:02:00Z");

            service.index(new LogEvent(
                    start, "INFO", "api", "Start event"
            ));
            service.index(new LogEvent(
                    middle, "INFO", "api", "Middle event"
            ));
            service.index(new LogEvent(
                    end, "INFO", "api", "End event"
            ));
            service.index(new LogEvent(
                    end.plusSeconds(1), "INFO", "api", "Later event"
            ));

            var results = service.search(new LogSearchCriteria(
                    null, null, null, start, end, 10
            ));

            assertEquals(3, results.size());
            assertTrue(results.stream().anyMatch(e -> e.timestamp().equals(start)));
            assertTrue(results.stream().anyMatch(e -> e.timestamp().equals(end)));
        }
    }

    @Test
    void combinesMessageLevelAndServiceFilters() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:00:00Z"),
                    "ERROR", "payment", "Database connection failed"
            ));
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:01:00Z"),
                    "INFO", "payment", "Database connection recovered"
            ));
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:02:00Z"),
                    "ERROR", "inventory", "Database connection failed"
            ));

            var results = service.search(new LogSearchCriteria(
                    "Database connection failed",
                    "ERROR",
                    "payment",
                    null,
                    null,
                    10
            ));

            assertEquals(1, results.size());
            assertEquals("payment", results.get(0).service());
            assertEquals("ERROR", results.get(0).level());
        }
    }

    @Test
    void returnsAllEventsWhenNoFiltersAreProvided() throws Exception {
        try (LuceneIndexService service = new LuceneIndexService()) {
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:00:00Z"),
                    "INFO", "api", "First event"
            ));
            service.index(new LogEvent(
                    Instant.parse("2026-01-01T10:01:00Z"),
                    "ERROR", "payment", "Second event"
            ));

            var results = service.search(new LogSearchCriteria(
                    null, null, null, null, null, 10
            ));

            assertEquals(2, results.size());
        }
    }

}
