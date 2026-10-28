package com.axlero.logstream.api;

import com.axlero.logstream.pipeline.LogIndexer;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.range.LongRange;
import org.apache.lucene.facet.range.LongRangeFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TimeSeriesService {
    private final LogIndexer indexer;

    public TimeSeriesService(LogIndexer indexer) {
        this.indexer = indexer;
    }

    public List<Map<String, Object>> histogram(long from, long to, long bucketMs, Query filter) throws IOException {
        List<LongRange> ranges = new ArrayList<>();
        for (long s = from; s < to; s += bucketMs) {
            ranges.add(new LongRange(String.valueOf(s), s, true, Math.min(s + bucketMs, to), false));
        }

        try (DirectoryReader reader = DirectoryReader.open(indexer.writer())) {
            IndexSearcher searcher = new IndexSearcher(reader);
            FacetsCollector fc = searcher.search(filter, new FacetsCollectorManager());
            Facets facets = new LongRangeFacetCounts("timestamp", fc, ranges.toArray(new LongRange[0]));
            FacetResult result = facets.getAllChildren("timestamp");

            List<Map<String, Object>> out = new ArrayList<>();
            for (LabelAndValue lv : result.labelValues) {
                out.add(Map.of("time", Long.parseLong(lv.label), "count", lv.value.intValue()));
            }
            return out;
        }
    }
}
