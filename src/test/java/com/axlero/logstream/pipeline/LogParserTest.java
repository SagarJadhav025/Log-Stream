package com.axlero.logstream.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogParserTest {
    @Test
    void parsesValidLine() {
        LogEntry e = LogParser.parse("2026-10-26T10:15:30Z error billing-api 1250 Payment DB timeout");
        assertEquals("ERROR", e.level());
        assertEquals("billing-api", e.service());
        assertEquals(1250, e.responseTime());
        assertEquals("Payment DB timeout", e.message());
    }

    @Test
    void rejectsMalformedLine() {
        assertThrows(IllegalArgumentException.class, () -> LogParser.parse("bad line"));
    }
}
