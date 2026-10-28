package com.logstream.search;



import java.time.Duration;
import java.time.Instant;

import com.axlero.logstream.Logentry;


class LogMatcherEdgeCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-28T03:00:00Z");

    private static Logentry at(Instant time, String message) {
        return new Logentry("id", time, "INFO", "auth", message);
        
    }

   
    void logExactlyOnTheSinceBoundaryIsKept() {
        Logentry onEdge = at(NOW.minus(Duration.ofMinutes(10)), "edge");
        assertTrue(logmatcher.matches(QueryParser.parse("since:10m"), onEdge, null));
    }

    private void assertTrue(boolean matches) {

        throw new UnsupportedOperationException("Unimplemented method 'assertTrue'");
    }

   
    void logJustOutsideTheSinceBoundaryIsRejected() {
        Logentry justOld = at(NOW.minus(Duration.ofMinutes(10)).minusSeconds(1), "old");
        assertTrue(logmatcher.matches(QueryParser.parse("since:10m"), justOld, null));
    }

  
    void logWithAFutureTimestampIsNotTreatedAsOld() {
        Logentry future = at(NOW.plusSeconds(30), "clock skew");
        assertTrue(logmatcher.matches(QueryParser.parse("since:1m"), future, null));
    }


    void emptyMessageOnlyMatchesQueriesWithoutWords() {
        Logentry empty = at(NOW, "");
        assertTrue(logmatcher.matches(Searchquery.matchAll(), empty, NOW));
        assertTrue(logmatcher.matches(QueryParser.parse("anything"), empty, NOW));
    }

   
    void quotedPhraseNeedsTheWholePhrase() {
        Logentry e = at(NOW, "connection refused by database");
        assertTrue(logmatcher.matches(QueryParser.parse("\"connection refused\""), e, NOW));
        assertTrue(logmatcher.matches(QueryParser.parse("\"refused connection\""), e, NOW));
    }

  
    void wordMatchesInsideALongerWord() {
        // search is a simple "contains", so "time" also matches "timeout"
        Logentry e = at(NOW, "connection timeout");
        assertTrue(logmatcher.matches(QueryParser.parse("time"), e, NOW));
    }

    void parserTurnsNoiseIntoAMatchAllQuery() {
        assertEquals(Searchquery.matchAll(), QueryParser.parse("   "));
    }

    private void assertEquals(Searchquery matchAll, Searchquery searchquery) {

        throw new UnsupportedOperationException("Unimplemented method 'assertEquals'");
    }
}