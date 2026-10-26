
package com.axlero.logstream.indexing;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.queryparser.classic.QueryParser;
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
        Document document = new Document();

        document.add(new StringField(
                "timestamp",
                event.timestamp().toString(),
                Field.Store.YES
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
    ) throws IOException, org.apache.lucene.queryparser.classic.ParseException {

        if (searchText == null || searchText.isBlank()) {
            throw new IllegalArgumentException(
                    "searchText must not be blank"
            );
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        writer.commit();

        QueryParser parser = new QueryParser("message", analyzer);
        Query query = parser.parse(QueryParser.escape(searchText));

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            var topDocs = searcher.search(query, limit);

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
