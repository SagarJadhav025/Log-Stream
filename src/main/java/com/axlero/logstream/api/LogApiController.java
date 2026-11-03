
package com.axlero.logstream.api;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "http://localhost:3000")
public class LogApiController {

    private final TimeSeriesService service;

    public LogApiController(TimeSeriesService service) {
        this.service = service;
    }

    // GET /api/logs/histogram?minutes=60&bucketSeconds=300&level=ERROR&serviceName=billing-api&text=timeout
    @GetMapping("/histogram")
    public List<Map<String, Object>> histogram(
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "60") int bucketSeconds,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String text
    ) throws Exception {

        // Validate parameters before performing any calculations.
        validateParameters(minutes, bucketSeconds);

        long to = Instant.now().toEpochMilli();
        long from = to - minutes * 60_000L;

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasFilter = false;

        // Level filter
        if (level != null && !level.isBlank()) {
            builder.add(
                    new TermQuery(
                            new Term("level", level.toUpperCase(Locale.ROOT))
                    ),
                    BooleanClause.Occur.MUST
            );
            hasFilter = true;
        }

        // Service name filter
        if (serviceName != null && !serviceName.isBlank()) {
            builder.add(
                    new TermQuery(new Term("service", serviceName)),
                    BooleanClause.Occur.MUST
            );
            hasFilter = true;
        }

        // Message text filter
        if (text != null && !text.isBlank()) {
            builder.add(
                    new QueryParser("message", new StandardAnalyzer())
                            .parse(text),
                    BooleanClause.Occur.MUST
            );
            hasFilter = true;
        }

        Query filter = hasFilter
                ? builder.build()
                : new MatchAllDocsQuery();

        return service.histogram(
                from,
                to,
                bucketSeconds * 1_000L,
                filter
        );
    }

    /**
     * Validates the histogram API parameters.
     */
    private void validateParameters(int minutes, int bucketSeconds) {

        if (minutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'minutes' must be greater than zero"
            );
        }

        if (bucketSeconds <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'bucketSeconds' must be greater than zero"
            );
        }
    }
}
