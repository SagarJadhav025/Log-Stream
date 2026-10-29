package com.axlero.logstream.api;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
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
            @RequestParam(required = false) String text) throws Exception {

        long to = Instant.now().toEpochMilli();
        long from = to - minutes * 60_000L;

        BooleanQuery.Builder b = new BooleanQuery.Builder();
        boolean any = false;
        if (level != null && !level.isBlank()) {
            b.add(new TermQuery(new Term("level", level.toUpperCase())), BooleanClause.Occur.MUST);
            any = true;
        }
        if (serviceName != null && !serviceName.isBlank()) {
            b.add(new TermQuery(new Term("service", serviceName)), BooleanClause.Occur.MUST);
            any = true;
        }
        if (text != null && !text.isBlank()) {
            b.add(new QueryParser("message", new StandardAnalyzer()).parse(text), BooleanClause.Occur.MUST);
            any = true;
        }
        Query filter = any ? b.build() : new MatchAllDocsQuery();

        return service.histogram(from, to, bucketSeconds * 1000L, filter);
    }
}
