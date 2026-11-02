package com.logstream.search;

import com.axlero.logstream.Logentry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public final class Loghistogram {

   
    public static final int MAX_BUCKETS = 2000;

    public record Bucket(Instant start, int count) {
    }

    private Loghistogram() {
    }

    public static List<Bucket> build(List<Logentry> entries, Duration bucketSize) {
        if (bucketSize == null || bucketSize.isZero() || bucketSize.isNegative()) {
            throw new IllegalArgumentException("bucket size must be greater than 0");
        }
        if (entries.isEmpty()) {
            return List.of();
        }

        long size = bucketSize.toMillis();
        if (size <= 0) {
            throw new IllegalArgumentException("bucket size must be at least 1 millisecond");
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (Logentry e : entries) {
            long t = e.timestamp().toEpochMilli();
            min = Math.min(min, t);
            max = Math.max(max, t);
        }

        long firstBucket = Math.floorDiv(min, size);
        long lastBucket = Math.floorDiv(max, size);
        long bucketCount = lastBucket - firstBucket + 1;
        if (bucketCount > MAX_BUCKETS) {
            throw new IllegalArgumentException(
                    "too many buckets (" + bucketCount + "), use a bigger bucket size");
        }

        int[] counts = new int[(int) bucketCount];
        for (Logentry e : entries) {
            long index = Math.floorDiv(e.timestamp().toEpochMilli(), size) - firstBucket;
            counts[(int) index]++;
        }

        List<Bucket> result = new ArrayList<>(counts.length);
        for (int i = 0; i < counts.length; i++) {
            result.add(new Bucket(Instant.ofEpochMilli((firstBucket + i) * size), counts[i]));
        }
        return result;
    }
}
