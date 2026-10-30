package com.axlero.logstream.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import com.axlero.logstream.Logentry;
import com.axlero.logstream.indexing.LogEvent;
import com.axlero.logstream.indexing.LogSearchCriteria;
import com.axlero.logstream.indexing.LuceneIndexService;

/** Lucene-backed implementation of the application log search contract. */
@Service
public final class LuceneLogSearchService implements Logsearchservice, AutoCloseable {

    private final LuceneIndexService indexService;

    /** Creates the single in-memory index used by the Spring-managed adapter. */
    public LuceneLogSearchService() {
        this(createIndexService());
    }

    /** Accepts an index service directly for tests and explicit composition. */
    public LuceneLogSearchService(LuceneIndexService indexService) {
        this.indexService = Objects.requireNonNull(indexService, "indexService must not be null");
    }

    @Override
    public void add(Logentry entry) {
        Objects.requireNonNull(entry, "entry must not be null");

        try {
            indexService.index(new LogEvent(
                    entry.timestamp(),
                    entry.level(),
                    entry.service(),
                    entry.message(),
                    entry.id()
            ));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to index log entry", e);
        }
    }

    @Override
    public List<Logentry> search(Searchquery query) {
        Objects.requireNonNull(query, "query must not be null");

        Instant from = null;
        if (query.since() != null) {
            if (query.since().isNegative()) {
                throw new IllegalArgumentException("since duration must not be negative");
            }
            try {
                from = Instant.now().minus(query.since());
            } catch (DateTimeException | ArithmeticException e) {
                throw new IllegalArgumentException("since duration is outside the supported range", e);
            }
        }

        LogSearchCriteria criteria = new LogSearchCriteria(
                null,
                query.level(),
                query.service(),
                from,
                null,
                query.limit()
        );

        try {
            return indexService.search(criteria, query.terms(), true, true).stream()
                    .map(event -> new Logentry(
                            event.id(),
                            event.timestamp(),
                            event.level(),
                            event.service(),
                            event.message()
                    ))
                    .toList();
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            throw new IllegalArgumentException("Invalid search terms", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to search logs", e);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        try {
            indexService.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close Lucene log search service", e);
        }
    }

    private static LuceneIndexService createIndexService() {
        try {
            return new LuceneIndexService();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize Lucene log search service", e);
        }
    }
}
