package com.axlero.logstream.pipeline;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.axlero.logstream.api.TimeSeriesService;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeSeriesServiceTest {

    @TempDir
    Path tempDir;

    private LogIndexer indexer;
    private TimeSeriesService timeSeriesService;

    @BeforeEach
    void setUp() throws Exception {
        indexer = new LogIndexer();

        ReflectionTestUtils.setField(
                indexer,
                "indexDir",
                tempDir.resolve("lucene-index").toString()
        );

        indexer.init();
        timeSeriesService = new TimeSeriesService(indexer);
    }

    @AfterEach
    void tearDown() throws Exception {
        indexer.close();
    }

    @Test
    void createsBucketsAndIncludesEmptyIntervals() throws Exception {

        indexer.index(new LogEntry(
                0L, "ERROR", "payment", 100L, "First error"
        ));

        indexer.index(new LogEntry(
                9_999L, "ERROR", "payment", 200L, "Second error"
        ));

        indexer.index(new LogEntry(
                20_000L, "ERROR", "payment", 300L, "Third error"
        ));

        List<Map<String, Object>> results =
                timeSeriesService.histogram(
                        0L,
                        30_000L,
                        10_000L,
                        new MatchAllDocsQuery()
                );

        assertEquals(3, results.size());

        assertEquals(0L, results.get(0).get("time"));
        assertEquals(2, results.get(0).get("count"));

        assertEquals(10_000L, results.get(1).get("time"));
        assertEquals(0, results.get(1).get("count"));

        assertEquals(20_000L, results.get(2).get("time"));
        assertEquals(1, results.get(2).get("count"));
    }

    @Test
    void usesHalfOpenBucketBoundaries() throws Exception {

        indexer.index(new LogEntry(
                0L, "INFO", "api", 100L, "Start"
        ));

        indexer.index(new LogEntry(
                10_000L, "INFO", "api", 100L, "Boundary"
        ));

        indexer.index(new LogEntry(
                29_999L, "INFO", "api", 100L, "Last"
        ));

        indexer.index(new LogEntry(
                30_000L, "INFO", "api", 100L, "Outside range"
        ));

        List<Map<String, Object>> results =
                timeSeriesService.histogram(
                        0L,
                        30_000L,
                        10_000L,
                        new MatchAllDocsQuery()
                );

        assertEquals(3, results.size());
        assertEquals(1, results.get(0).get("count"));
        assertEquals(1, results.get(1).get("count"));
        assertEquals(1, results.get(2).get("count"));
    }
    @Test
    void rejectsInvalidTimeRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> timeSeriesService.histogram(
                        10_000L,
                        10_000L,
                        1_000L,
                        new MatchAllDocsQuery()
                )
        );
    }

    @Test
    void rejectsNonPositiveBucketSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> timeSeriesService.histogram(
                        0L,
                        10_000L,
                        0L,
                        new MatchAllDocsQuery()
                )
        );
    }
    @Test
    void supportsPartialFinalBucket() throws Exception {
        indexer.index(new LogEntry(
                24_999L, "INFO", "api", 100L, "Final bucket"
        ));

        List<Map<String, Object>> results =
                timeSeriesService.histogram(
                        0L,
                        25_000L,
                        10_000L,
                        new MatchAllDocsQuery()
                );

        assertEquals(3, results.size());
        assertEquals(0L, results.get(0).get("time"));
        assertEquals(0, results.get(0).get("count"));
        assertEquals(10_000L, results.get(1).get("time"));
        assertEquals(0, results.get(1).get("count"));
        assertEquals(20_000L, results.get(2).get("time"));
        assertEquals(1, results.get(2).get("count"));
    }
    @Test
    void handlesBucketSizeLargerThanTimeRange() throws Exception {
        indexer.index(new LogEntry(
                5_000L, "INFO", "api", 100L, "Test event"
        ));

        List<Map<String, Object>> results =
                timeSeriesService.histogram(
                        0L,
                        10_000L,
                        Long.MAX_VALUE,
                        new MatchAllDocsQuery()
                );

        assertEquals(1, results.size());
        assertEquals(0L, results.get(0).get("time"));
        assertEquals(1, results.get(0).get("count"));
    }
    @Test
    void handlesTimeRangeNearLongMaxValue() throws Exception {
        long from = Long.MAX_VALUE - 20_000L;
        long to = Long.MAX_VALUE - 5_000L;

        indexer.index(new LogEntry(
                Long.MAX_VALUE - 10_000L,
                "INFO",
                "api",
                100L,
                "Near maximum timestamp"
        ));

        List<Map<String, Object>> results =
                timeSeriesService.histogram(
                        from,
                        to,
                        10_000L,
                        new MatchAllDocsQuery()
                );

        assertEquals(2, results.size());
        assertEquals(from, results.get(0).get("time"));
        assertEquals(0, results.get(0).get("count"));
        assertEquals(from + 10_000L, results.get(1).get("time"));
        assertEquals(1, results.get(1).get("count"));
    }
    @Test
    void handlesTimeRangeNearLongMinValue() throws Exception {
        long from = Long.MIN_VALUE + 5_000L;
        long to = Long.MIN_VALUE + 25_000L;

        indexer.index(new LogEntry(
                Long.MIN_VALUE + 15_000L,
                "INFO",
                "api",
                100L,
                "Near minimum timestamp"
        ));

        List<Map<String, Object>> results =
                timeSeriesService.histogram(
                        from,
                        to,
                        10_000L,
                        new MatchAllDocsQuery()
                );

        assertEquals(2, results.size());
        assertEquals(from, results.get(0).get("time"));
        assertEquals(0, results.get(0).get("count"));
        assertEquals(from + 10_000L, results.get(1).get("time"));
        assertEquals(1, results.get(1).get("count"));
    }
    @Test
    void calculatesResponseTimeMetricsForEachBucket() throws Exception {

        indexer.index(new LogEntry(
                1_000L, "INFO", "api", 100L, "Request one"
        ));

        indexer.index(new LogEntry(
                2_000L, "INFO", "api", 200L, "Request two"
        ));

        indexer.index(new LogEntry(
                3_000L, "INFO", "api", 300L, "Request three"
        ));

        indexer.index(new LogEntry(
                12_000L, "ERROR", "payment", 500L, "Payment error"
        ));

        List<Map<String, Object>> results =
                timeSeriesService.histogram(
                        0L,
                        20_000L,
                        10_000L,
                        new MatchAllDocsQuery()
                );

        assertEquals(2, results.size());

        // First bucket: three events
        Map<String, Object> firstBucket = results.get(0);

        assertEquals(3, firstBucket.get("count"));
        assertEquals(200.0, firstBucket.get("avgResponseTime"));
        assertEquals(100L, firstBucket.get("minResponseTime"));
        assertEquals(300L, firstBucket.get("maxResponseTime"));

        // Second bucket: one event
        Map<String, Object> secondBucket = results.get(1);

        assertEquals(1, secondBucket.get("count"));
        assertEquals(500.0, secondBucket.get("avgResponseTime"));
        assertEquals(500L, secondBucket.get("minResponseTime"));
        assertEquals(500L, secondBucket.get("maxResponseTime"));
    }
    @Test
    void returnsNullResponseMetricsForEmptyBuckets() throws Exception {

        List<Map<String, Object>> results =
                timeSeriesService.histogram(
                        0L,
                        20_000L,
                        10_000L,
                        new MatchAllDocsQuery()
                );

        assertEquals(2, results.size());

        for (Map<String, Object> bucket : results) {
            assertEquals(0, bucket.get("count"));
            assertNull(bucket.get("avgResponseTime"));
            assertNull(bucket.get("minResponseTime"));
            assertNull(bucket.get("maxResponseTime"));
        }
    }
}
