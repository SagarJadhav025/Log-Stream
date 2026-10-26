
package com.axlero.logstream.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
