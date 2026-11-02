package com.axlero.logstream.search;

import com.axlero.logstream.Logentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoint for log search queries.
 * GET /api/search?q=level:ERROR service:auth&limit=50&sinceMinutes=60
 */
@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = "*")
public class SearchApiController {

    private static final Logger logger = LoggerFactory.getLogger(SearchApiController.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final Logsearchservice searchService;

    public SearchApiController(Logsearchservice searchService) {
        this.searchService = searchService;
    }

    /**
     * Search logs with an optional Lucene-style query string.
     *
     * @param q            Free-text / Lucene query (e.g. {@code level:ERROR service:auth "timeout"})
     * @param limit        Maximum results to return (default 50, max 500)
     * @param sinceMinutes Only return logs from the last N minutes (optional)
     * @param level        Convenience filter by log level (optional, overridden by q)
     * @param service      Convenience filter by service name (optional)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Integer sinceMinutes,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String service) {

        long startMs = System.currentTimeMillis();

        // Clamp limit
        int safeLimit = Math.min(Math.max(1, limit), MAX_LIMIT);

        // Parse q into a list of whitespace-separated terms
        List<String> termList = q.isBlank()
                ? List.of()
                : Arrays.stream(q.trim().split("\\s+"))
                        .filter(t -> !t.isBlank())
                        .collect(Collectors.toList());
        String lvl   = (level != null && !level.isBlank()) ? level.toUpperCase() : null;
        String svc   = (service != null && !service.isBlank()) ? service : null;
        Duration since = sinceMinutes != null ? Duration.ofMinutes(sinceMinutes) : null;

        Searchquery query = new Searchquery(termList, lvl, svc, since, safeLimit);

        try {
            List<Logentry> entries = searchService.search(query);
            long tookMs = System.currentTimeMillis() - startMs;

            logger.debug("Search q='{}' level='{}' service='{}' -> {} results in {}ms",
                    q, lvl, svc, entries.size(), tookMs);

            return ResponseEntity.ok(Map.of(
                    "results", entries,
                    "count",   entries.size(),
                    "tookMs",  tookMs,
                    "query",   q
            ));

        } catch (Exception e) {
            logger.error("Search failed for q='{}': {}", q, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error",  "Search failed: " + e.getMessage(),
                    "results", List.of(),
                    "count",  0,
                    "tookMs", System.currentTimeMillis() - startMs
            ));
        }
    }
}
