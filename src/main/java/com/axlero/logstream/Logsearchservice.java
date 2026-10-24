package com.logstream.search;

import com.logstream.model.LogEntry;

import java.util.List;


public interface LogSearchService {

    /** store one log so it can be searched later */
    void add(LogEntry entry);

    /** newest-first matches, at most query.limit() */
    List<LogEntry> search(SearchQuery query);
}