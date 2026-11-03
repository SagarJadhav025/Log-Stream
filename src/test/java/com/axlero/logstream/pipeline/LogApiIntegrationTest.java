
package com.axlero.logstream.pipeline;

import com.axlero.logstream.api.LogApiController;
import com.axlero.logstream.api.TimeSeriesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogApiIntegrationTest {

    @TempDir
    Path tempDir;

    private LogIndexer indexer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {

        // Create a real Lucene index in a temporary directory.
        indexer = new LogIndexer();

        ReflectionTestUtils.setField(
                indexer,
                "indexDir",
                tempDir.resolve("lucene-index").toString()
        );

        indexer.init();

        // Use the real time-series service and controller.
        TimeSeriesService timeSeriesService =
                new TimeSeriesService(indexer);

        LogApiController controller =
                new LogApiController(timeSeriesService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (indexer != null) {
            indexer.close();
        }
    }

    @Test
    void filtersActualIndexedLogsByErrorLevel() throws Exception {

        long now = Instant.now().toEpochMilli();

        // ERROR log: should be included.
        indexer.index(new LogEntry(
                now - 3_000L,
                "ERROR",
                "billing-api",
                100L,
                "Database connection failed"
        ));

        // INFO log: should be excluded.
        indexer.index(new LogEntry(
                now - 2_000L,
                "INFO",
                "billing-api",
                500L,
                "Request completed successfully"
        ));

        // Another ERROR log: should be included.
        indexer.index(new LogEntry(
                now - 1_000L,
                "ERROR",
                "payments-api",
                300L,
                "Payment processing failed"
        ));

        mockMvc.perform(
                        get("/api/logs/histogram")
                                .param("minutes", "5")
                                .param("bucketSeconds", "300")
                                .param("level", "ERROR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].avgResponseTime").value(200.0))
                .andExpect(jsonPath("$[0].minResponseTime").value(100))
                .andExpect(jsonPath("$[0].maxResponseTime").value(300));
    }

    @Test
    void filtersActualIndexedLogsByServiceName() throws Exception {

        long now = Instant.now().toEpochMilli();

        indexer.index(new LogEntry(
                now - 3_000L,
                "ERROR",
                "billing-api",
                100L,
                "Billing failed"
        ));

        indexer.index(new LogEntry(
                now - 2_000L,
                "ERROR",
                "payments-api",
                500L,
                "Payment failed"
        ));

        indexer.index(new LogEntry(
                now - 1_000L,
                "INFO",
                "billing-api",
                300L,
                "Billing completed"
        ));

        mockMvc.perform(
                        get("/api/logs/histogram")
                                .param("minutes", "5")
                                .param("bucketSeconds", "300")
                                .param("serviceName", "billing-api")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].avgResponseTime").value(200.0))
                .andExpect(jsonPath("$[0].minResponseTime").value(100))
                .andExpect(jsonPath("$[0].maxResponseTime").value(300));
    }
    @Test
    void filtersActualIndexedLogsByText() throws Exception {
        long now = Instant.now().toEpochMilli();

        indexer.index(new LogEntry(
                now - 3_000L, "ERROR", "billing-api",
                100L, "Database timeout occurred"
        ));

        indexer.index(new LogEntry(
                now - 2_000L, "ERROR", "payments-api",
                500L, "Payment processing failed"
        ));

        indexer.index(new LogEntry(
                now - 1_000L, "INFO", "billing-api",
                300L, "Database connection restored"
        ));

        mockMvc.perform(get("/api/logs/histogram")
                        .param("minutes", "5")
                        .param("bucketSeconds", "300")
                        .param("text", "timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].avgResponseTime").value(100.0))
                .andExpect(jsonPath("$[0].minResponseTime").value(100))
                .andExpect(jsonPath("$[0].maxResponseTime").value(100));
    }
    @Test
    void combinesActualIndexedLogFilters() throws Exception {
        long now = Instant.now().toEpochMilli();

        indexer.index(new LogEntry(
                now - 4_000L, "ERROR", "billing-api",
                100L, "Database timeout occurred"
        ));

        indexer.index(new LogEntry(
                now - 3_000L, "ERROR", "payments-api",
                500L, "Database timeout occurred"
        ));

        indexer.index(new LogEntry(
                now - 2_000L, "INFO", "billing-api",
                300L, "Database timeout occurred"
        ));

        indexer.index(new LogEntry(
                now - 1_000L, "ERROR", "billing-api",
                200L, "Payment processing failed"
        ));

        mockMvc.perform(get("/api/logs/histogram")
                        .param("minutes", "5")
                        .param("bucketSeconds", "300")
                        .param("level", "ERROR")
                        .param("serviceName", "billing-api")
                        .param("text", "timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].avgResponseTime").value(100.0))
                .andExpect(jsonPath("$[0].minResponseTime").value(100))
                .andExpect(jsonPath("$[0].maxResponseTime").value(100));
    }
}
