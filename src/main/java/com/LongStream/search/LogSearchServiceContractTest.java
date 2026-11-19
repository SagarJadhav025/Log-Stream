
package com.LongStream.search;

import java.time.Instant;
import java.util.List;

import com.axlero.logstream.Logentry;
import com.logstream.search.InMemoryLogSearchService;
import com.logstream.search.Logsearchservice;
import com.logstream.search.QueryParser;
import com.logstream.search.Searchquery;



public abstract class LogSearchServiceContractTest {

    
    protected abstract InMemoryLogSearchService create();

    
    private static Logentry log(String id, String level, String service, String message, long secondsAgo) {
        return new Logentry(id, Instant.now().minusSeconds(secondsAgo), level, service, message);
    }

    private static List<String> ids(List<Logentry> logs) {
        return logs.stream().map(Logentry::id).toList();
    }

    
    void emptyServiceReturnsNothing() {
        Logsearchservice s = (Logsearchservice) create();
        assertTrue(s.search(Searchquery.matchAll()).isEmpty());
    }

    
    void addedLogCanBeFound() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("1", "INFO", "auth", "user login successful", 5));
        assertEquals(List.of("1"), ids(s.search(Searchquery.matchAll())));
    }

  
    protected abstract void assertEquals(int of, int ids);


    void resultsAreNewestFirst() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("old", "INFO", "auth", "a", 30));
        s.add(log("middle", "INFO", "auth", "b", 20));
        s.add(log("new", "INFO", "auth", "c", 10));
        assertEquals(List.of("new", "middle", "old"), ids(s.search(Searchquery.matchAll())));
    }

  
    protected abstract void assertEquals(List<String> of, List<String> ids);


    void limitIsRespected() {
        Logsearchservice s = (Logsearchservice) create();
        for (int i = 0; i < 10; i++) {
            s.add(log("id" + i, "INFO", "auth", "m", 100 - i));
        }
        Searchquery three = Searchquery.matchAll().withLimit(3);
        assertEquals(3, s.search(three).size());
    }

    
    void levelFilterOnlyReturnsThatLevel() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("1", "INFO", "auth", "ok", 30));
        s.add(log("2", "ERROR", "auth", "boom", 20));
        s.add(log("3", "WARN", "auth", "slow", 10));
        assertEquals(List.of("2"), ids(s.search(QueryParser.parse("level:ERROR"))));
    }

    
    void serviceFilterOnlyReturnsThatService() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("1", "INFO", "auth", "x", 30));
        s.add(log("2", "INFO", "payment", "x", 20));
        assertEquals(List.of("2"), ids(s.search(QueryParser.parse("service:payment"))));
    }

    
    void everyWordMustAppearInTheMessage() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("1", "ERROR", "orders", "connection timeout while calling inventory", 30));
        s.add(log("2", "ERROR", "orders", "connection refused by database", 20));
        assertEquals(List.of("1"), ids(s.search(QueryParser.parse("connection timeout"))));
    }

  
    void wordSearchIgnoresCase() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("1", "INFO", "auth", "User Login Successful", 5));
        assertEquals(List.of("1"), ids(s.search(QueryParser.parse("login SUCCESSFUL"))));
    }

    
    void sinceFilterLeavesOutOldLogs() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("old", "INFO", "auth", "x", 3600)); // one hour ago
        s.add(log("recent", "INFO", "auth", "x", 30));
        assertEquals(List.of("recent"), ids(s.search(QueryParser.parse("since:15m"))));
    }

    
    void filtersCanBeCombined() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("1", "ERROR", "payment", "payment declined by bank", 30));
        s.add(log("2", "ERROR", "auth", "payment declined by bank", 20));
        s.add(log("3", "INFO", "payment", "payment declined by bank", 10));
        assertEquals(List.of("1"), ids(s.search(QueryParser.parse("level:ERROR service:payment declined"))));
    }

    
    void searchWithNoMatchGivesAnEmptyList() {
        Logsearchservice s = (Logsearchservice) create();
        s.add(log("1", "INFO", "auth", "hello", 5));
        assertTrue(s.search(QueryParser.parse("zzz-nothing-like-this")).isEmpty());
    }


    protected abstract void assertTrue(boolean empty);
}