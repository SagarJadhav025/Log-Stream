package com.axlero.logstream.api;

import com.axlero.logstream.pipeline.LogIndexer;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TimeSeriesService {

    private final LogIndexer indexer;

    public TimeSeriesService(LogIndexer indexer) {
        this.indexer = indexer;
    }

    private static class BucketStats {
        long count;
        double sum;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        void add(long responseTime) {
            count++;
            sum += responseTime;
            min = Math.min(min, responseTime);
            max = Math.max(max, responseTime);
        }

        Map<String, Object> toMap(long time) {
            Map<String, Object> result = new HashMap<>();

            result.put("time", time);

            // Keep the count field compatible with the previous API response.
            result.put("count", Math.toIntExact(count));

            if (count == 0) {
                result.put("avgResponseTime", null);
                result.put("minResponseTime", null);
                result.put("maxResponseTime", null);
            } else {
                result.put("avgResponseTime", sum / count);
                result.put("minResponseTime", min);
                result.put("maxResponseTime", max);
            }

            return result;
        }
    }

    public List<Map<String, Object>> histogram(
            long from,
            long to,
            long bucketMs,
            Query filter
    ) throws IOException {

        if (from >= to) {
            throw new IllegalArgumentException(
                    "'from' must be less than 'to'"
            );
        }

        if (bucketMs <= 0) {
            throw new IllegalArgumentException(
                    "'bucketMs' must be greater than zero"
            );
        }

        if (filter == null) {
            throw new IllegalArgumentException(
                    "'filter' must not be null"
            );
        }

        List<Long> bucketStarts = new ArrayList<>();
        List<BucketStats> stats = new ArrayList<>();

        BigInteger current = BigInteger.valueOf(from);
        BigInteger endTime = BigInteger.valueOf(to);
        BigInteger bucketSize = BigInteger.valueOf(bucketMs);

        while (current.compareTo(endTime) < 0) {
            if (stats.size() == Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Time range produces too many buckets"
                );
            }

            bucketStarts.add(current.longValueExact());
            stats.add(new BucketStats());

            current = current.add(bucketSize).min(endTime);
        }

        try (DirectoryReader reader =
                     DirectoryReader.open(indexer.writer())) {

            IndexSearcher searcher = new IndexSearcher(reader);

            FacetsCollector collector = new FacetsCollector();
            searcher.search(filter, collector);

            for (FacetsCollector.MatchingDocs matchingDocs :
                    collector.getMatchingDocs()) {

                LeafReaderContext leaf = matchingDocs.context();

                NumericDocValues timestamps =
                        leaf.reader().getNumericDocValues("timestamp");

                NumericDocValues responseTimes =
                        leaf.reader().getNumericDocValues("response_time");

                if (timestamps == null || responseTimes == null) {
                    continue;
                }

                DocIdSet matchedDocs = matchingDocs.bits();

                DocIdSetIterator iterator = matchedDocs == null
                        ? null
                        : matchedDocs.iterator();

                if (matchedDocs != null && iterator == null) {
                    continue;
                }

                if (matchedDocs == null) {
                    // Every document in this segment matched the query.
                    for (int doc = 0;
                         doc < leaf.reader().maxDoc();
                         doc++) {

                        processDocument(
                                doc,
                                timestamps,
                                responseTimes,
                                from,
                                to,
                                bucketMs,
                                stats
                        );
                    }
                } else {
                    int doc;

                    while ((doc = iterator.nextDoc())
                            != DocIdSetIterator.NO_MORE_DOCS) {

                        processDocument(
                                doc,
                                timestamps,
                                responseTimes,
                                from,
                                to,
                                bucketMs,
                                stats
                        );
                    }
                }
            }
        }

        List<Map<String, Object>> output = new ArrayList<>();

        for (int i = 0; i < bucketStarts.size(); i++) {
            output.add(stats.get(i).toMap(bucketStarts.get(i)));
        }

        return output;
    }

    private static void processDocument(
            int doc,
            NumericDocValues timestamps,
            NumericDocValues responseTimes,
            long from,
            long to,
            long bucketMs,
            List<BucketStats> stats
    ) throws IOException {

        if (!timestamps.advanceExact(doc)) {
            return;
        }

        if (!responseTimes.advanceExact(doc)) {
            return;
        }

        long timestamp = timestamps.longValue();
        long responseTime = responseTimes.longValue();

        // The histogram uses the half-open interval [from, to).
        if (timestamp < from || timestamp >= to) {
            return;
        }

        // BigInteger prevents overflow when timestamps cross long boundaries.
        BigInteger distance = BigInteger.valueOf(timestamp)
                .subtract(BigInteger.valueOf(from));

        int bucketIndex = distance
                .divide(BigInteger.valueOf(bucketMs))
                .intValueExact();

        if (bucketIndex >= 0 && bucketIndex < stats.size()) {
            stats.get(bucketIndex).add(responseTime);
        }
    }
}
