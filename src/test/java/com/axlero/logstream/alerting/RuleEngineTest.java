package com.axlero.logstream.alerting;

import com.axlero.logstream.alerting.engine.RuleEngine;
import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.pipeline.LogEntry;
import com.axlero.logstream.pipeline.LogIndexer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    @TempDir
    Path tempDir;

    private LogIndexer indexer;
    private RuleEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        indexer = new LogIndexer(tempDir.toString());
        indexer.init();
        engine = new RuleEngine(indexer);
    }

    @AfterEach
    void tearDown() throws IOException {
        indexer.close();
    }

    @Test
    void detectsBreachWhenThresholdReached() throws IOException {
        long now = Instant.now().toEpochMilli();

        // Index 3 errors within the last 2 minutes
        indexer.index(new LogEntry(now - 30_000, "ERROR", "billing-api", 500, "Database timeout exception"));
        indexer.index(new LogEntry(now - 20_000, "ERROR", "billing-api", 650, "Connection timeout to Postgres"));
        indexer.index(new LogEntry(now - 10_000, "ERROR", "billing-api", 400, "Service timeout"));
        indexer.commit();

        AlertRule rule = AlertRule.of("r1", "Billing Timeouts", "message:timeout AND level:ERROR", 3, 5, "simulated://test");

        Optional<AlertBreachEvent> breachOpt = engine.evaluate(rule);

        assertTrue(breachOpt.isPresent());
        AlertBreachEvent breach = breachOpt.get();
        assertEquals("r1", breach.ruleId());
        assertEquals(3, breach.actualCount());
        assertEquals(3, breach.threshold());
        assertFalse(breach.sampleLogs().isEmpty());
    }

    @Test
    void returnsEmptyWhenThresholdNotMet() throws IOException {
        long now = Instant.now().toEpochMilli();

        indexer.index(new LogEntry(now - 10_000, "ERROR", "billing-api", 500, "Single error timeout"));
        indexer.commit();

        AlertRule rule = AlertRule.of("r2", "High Volume", "message:timeout", 5, 5, "simulated://test");

        Optional<AlertBreachEvent> breachOpt = engine.evaluate(rule);
        assertFalse(breachOpt.isPresent());
    }

    @Test
    void ignoresDisabledRules() {
        AlertRule disabled = new AlertRule("r3", "Disabled", "level:ERROR", 1, 5, 5, null, null, null, false);
        Optional<AlertBreachEvent> breachOpt = engine.evaluate(disabled);
        assertFalse(breachOpt.isPresent());
    }
}
