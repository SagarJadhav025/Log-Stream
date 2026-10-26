
package com.axlero.logstream.indexing;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;

public final class LuceneIndexService implements AutoCloseable {

    private final ByteBuffersDirectory directory;
    private final StandardAnalyzer analyzer;
    private final IndexWriter writer;

    public LuceneIndexService() throws IOException {
        directory = new ByteBuffersDirectory();
        analyzer = new StandardAnalyzer();
        writer = new IndexWriter(
                directory,
                new IndexWriterConfig(analyzer)
        );
    }

    public void index(LogEvent event) throws IOException {
        Objects.requireNonNull(event, "event must not be null");

        Document document = new Document();

        document.add(new StringField(
                "timestamp",
                event.timestamp().toString(),
                Field.Store.YES
        ));

        // Numeric timestamp supports efficient range queries.
        // Range filtering uses millisecond precision.
        document.add(new LongPoint(
                "timestamp_epoch_millis",
                event.timestamp().toEpochMilli()
        ));

        document.add(new StoredField(
                "timestamp_epoch_millis",
                event.timestamp().toEpochMilli()
        ));

        document.add(new StringField(
                "level", event.level(), Field.Store.YES
        ));

        document.add(new StringField(
                "service", event.service(), Field.Store.YES
        ));

        document.add(new TextField(
                "message", event.message(), Field.Store.YES
        ));

        writer.addDocument(document);
    }

    public List<LogEvent> searchMessage(
            String searchText,
            int limit
    ) throws IOException,
            org.apache.lucene.queryparser.classic.ParseException {

        if (searchText == null || searchText.isBlank()) {
            throw new IllegalArgumentException(
                    "searchText must not be blank"
            );
        }

        return search(new LogSearchCriteria(
                searchText, null, null, null, null, limit
        ));
    }

    public List<LogEvent> search(
            LogSearchCriteria criteria
    ) throws IOException,
            org.apache.lucene.queryparser.classic.ParseException {

        Objects.requireNonNull(
                criteria, "criteria must not be null"
        );

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasFilter = false;

        if (criteria.message() != null) {
            QueryParser parser = new QueryParser("message", analyzer);
            Query messageQuery = parser.parse(
                    QueryParser.escape(criteria.message())
            );

            builder.add(messageQuery, BooleanClause.Occur.MUST);
            hasFilter = true;
        }

        if (criteria.level() != null) {
            builder.add(
                    new TermQuery(new Term("level", criteria.level())),
                    BooleanClause.Occur.MUST
            );
            hasFilter = true;
        }

        if (criteria.service() != null) {
            builder.add(
                    new TermQuery(new Term("service", criteria.service())),
                    BooleanClause.Occur.MUST
            );
            hasFilter = true;
        }

        if (criteria.from() != null || criteria.to() != null) {
            long lower = criteria.from() == null
                    ? Long.MIN_VALUE
                    : criteria.from().toEpochMilli();

            long upper = criteria.to() == null
                    ? Long.MAX_VALUE
                    : criteria.to().toEpochMilli();

            builder.add(
                    LongPoint.newRangeQuery(
                            "timestamp_epoch_millis", lower, upper
                    ),
                    BooleanClause.Occur.MUST
            );
            hasFilter = true;
        }

        Query query = hasFilter
                ? builder.build()
                : new MatchAllDocsQuery();

        writer.commit();

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            var topDocs = searcher.search(query, criteria.limit());

            List<LogEvent> results = new ArrayList<>();

            for (var scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields()
                        .document(scoreDoc.doc);

                results.add(new LogEvent(
                        Instant.parse(document.get("timestamp")),
                        document.get("level"),
                        document.get("service"),
                        document.get("message")
                ));
            }

            return List.copyOf(results);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            writer.close();
        } finally {
            try {
                analyzer.close();
            } finally {
                directory.close();
            }
        }
    }
}
