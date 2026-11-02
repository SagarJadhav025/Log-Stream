package com.axlero.logstream.alerting.engine;

import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.pipeline.LogIndexer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Evaluates configured alert rules against the Lucene index.
 */
@Component
public class RuleEngine {

    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);

    private final LogIndexer indexer;
    private final StandardAnalyzer analyzer;

    public RuleEngine(LogIndexer indexer) {
        this.indexer = indexer;
        this.analyzer = new StandardAnalyzer();
    }

    /**
     * Evaluates a single rule over its configured time window.
     *
     * @param rule AlertRule to evaluate
     * @return Optional containing AlertBreachEvent if the threshold was reached or exceeded
     */
    public Optional<AlertBreachEvent> evaluate(AlertRule rule) {
        if (!rule.enabled()) {
            return Optional.empty();
        }

        long toTime = Instant.now().toEpochMilli();
        long fromTime = toTime - (rule.windowMinutes() * 60_000L);

        try {
            Query timeRangeQuery = LongPoint.newRangeQuery("timestamp", fromTime, toTime);
            Query userQuery = parseUserQuery(rule.query());

            BooleanQuery combinedQuery = new BooleanQuery.Builder()
                    .add(timeRangeQuery, BooleanClause.Occur.MUST)
                    .add(userQuery, BooleanClause.Occur.MUST)
                    .build();

            return executeEvaluation(rule, combinedQuery);

        } catch (ParseException e) {
            logger.error("Failed to parse alert query '{}' for rule '{}': {}", rule.query(), rule.name(), e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            logger.error("IO error evaluating alert rule '{}': {}", rule.name(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AlertBreachEvent> executeEvaluation(AlertRule rule, Query query) throws IOException {
        if (indexer == null || indexer.writer() == null) {
            logger.warn("LogIndexer is not ready for rule evaluation");
            return Optional.empty();
        }

        try (DirectoryReader reader = DirectoryReader.open(indexer.writer())) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(query, 5);

            long matchedCount = topDocs.totalHits.value();

            if (matchedCount >= rule.threshold()) {
                logger.info("Alert breach detected for rule '{}' (id: {}): matched {} >= threshold {}",
                        rule.name(), rule.id(), matchedCount, rule.threshold());

                List<String> sampleLogs = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = searcher.storedFields().document(scoreDoc.doc);
                    String level = doc.get("level");
                    String service = doc.get("service");
                    String message = doc.get("message");
                    Number tsNum = doc.getField("timestamp_stored") != null ?
                            doc.getField("timestamp_stored").numericValue() : null;
                    String ts = tsNum != null ? Instant.ofEpochMilli(tsNum.longValue()).toString() : "N/A";

                    sampleLogs.add(String.format("[%s] [%s] %s - %s", ts, level, service, message));
                }

                return Optional.of(new AlertBreachEvent(
                        rule.id(),
                        rule.name(),
                        rule.query(),
                        rule.threshold(),
                        matchedCount,
                        rule.windowMinutes(),
                        Instant.now(),
                        rule.severity(),
                        sampleLogs
                ));
            }
        }

        return Optional.empty();
    }

    private Query parseUserQuery(String queryText) throws ParseException {
        if (queryText == null || queryText.isBlank() || queryText.equals("*:*")) {
            return new MatchAllDocsQuery();
        }
        QueryParser parser = new QueryParser("message", analyzer) {
            @Override
            protected Query newTermQuery(Term term, float boost) {
                if ("level".equalsIgnoreCase(term.field())) {
                    return super.newTermQuery(new Term("level", term.text().toUpperCase(Locale.ROOT)), boost);
                }
                return super.newTermQuery(term, boost);
            }
        };
        return parser.parse(queryText);
    }
}
