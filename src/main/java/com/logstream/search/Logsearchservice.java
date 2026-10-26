package com.logstream.search;

import java.util.List;

import com.axlero.logstream.Logentry;


public interface Logsearchservice {

    /** store one log so it can be searched later */
    void add(Logentry entry);

    /** newest-first matches, at most query.limit() */
    List<Logentry> search(Searchquery query);
}