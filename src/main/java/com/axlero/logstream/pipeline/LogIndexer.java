package com.axlero.logstream.pipeline;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class LogIndexer {
    @Value("${logstream.index-dir:./lucene-index}")
    private String indexDir;

    private FSDirectory directory;
    private IndexWriter writer;

    @PostConstruct
    void init() throws IOException {
        directory = FSDirectory.open(Path.of(indexDir));
        IndexWriterConfig cfg = new IndexWriterConfig(new StandardAnalyzer());
        cfg.setRAMBufferSizeMB(256);
        writer = new IndexWriter(directory, cfg);
    }

    public void index(LogEntry e) throws IOException {
        Document d = new Document();
        d.add(new StringField("level", e.level(), Field.Store.YES));
        d.add(new StringField("service", e.service(), Field.Store.YES));
        d.add(new TextField("message", e.message(), Field.Store.YES));
        d.add(new LongPoint("timestamp", e.timestamp()));
        d.add(new NumericDocValuesField("timestamp", e.timestamp()));
        d.add(new StoredField("timestamp_stored", e.timestamp()));
        d.add(new LongPoint("response_time", e.responseTime()));
        d.add(new NumericDocValuesField("response_time", e.responseTime()));
        d.add(new StoredField("response_time_stored", e.responseTime()));
        writer.addDocument(d);
    }

    public void commit() throws IOException { writer.commit(); }
    public IndexWriter writer() { return writer; }

    @PreDestroy
    void close() throws IOException { writer.close(); directory.close(); }
}
