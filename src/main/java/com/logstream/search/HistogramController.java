package com.logstream.search;

import com.axlero.logstream.Logentry;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@RestController
public class HistogramController {

    private final Logsearchservice searchService;

    public HistogramController(Logsearchservice searchService) {
        this.searchService = searchService;
    }

    public record HistogramResponse(String query, long bucketSeconds, int totalLogs,
                                    List<Loghistogram.Bucket> buckets) {
    }

    @GetMapping("/api/histogram")
    public HistogramResponse histogram(@RequestParam(name = "q", defaultValue = "") String q,
                                       @RequestParam(name = "bucket", defaultValue = "1m") String bucket) {
        Duration bucketSize = RelativeTime.parse(bucket);
        if (bucketSize == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "bucket must look like 30s, 5m, 1h or 1d");
        }

        Searchquery query = QueryParser.parse(q).withLimit(Searchquery.MAX_LIMIT);
        List<Logentry> matches = searchService.search(query);

        try {
            return new HistogramResponse(q, bucketSize.getSeconds(), matches.size(),
                    Loghistogram.build(matches, bucketSize));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
