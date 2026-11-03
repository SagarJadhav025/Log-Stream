package com.logstream.bench;

import com.axlero.logstream.Logentry;
import com.logstream.search.InMemoryLogSearchService;
import com.logstream.search.QueryParser;
import com.logstream.search.Searchquery;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class Searchbenchmark {

    private static final String[] LEVELS = {"INFO", "INFO", "INFO", "INFO", "DEBUG", "DEBUG", "WARN", "WARN", "ERROR"};
    private static final String[] SERVICES = {"auth", "payment", "orders", "gateway"};
    private static final String[] MESSAGES = {
            "user login successful", "user login failed: wrong password", "payment authorised",
            "payment declined by bank", "connection timeout while calling inventory service",
            "connection refused by database", "order created", "order cancelled by user",
            "cache miss for product list", "request completed in 120 ms", "slow query detected",
            "retrying request after failure"};

    private static final List<String> QUERIES = List.of(
            "level:ERROR",
            "service:auth timeout",
            "\"connection refused\"",
            "level:ERROR service:payment since:15m",
            "zzz-this-matches-nothing");

    private static final int RUNS = 20;

    public static void main(String[] args) {
        int[] sizes = {10_000, 100_000, 1_000_000};

        System.out.printf("%-10s %-40s %10s %10s%n", "logs", "query", "avg ms", "max ms");
        System.out.println("-".repeat(74));

        for (int size : sizes) {
            InMemoryLogSearchService service = fill(size);

            for (String text : QUERIES) {
                Searchquery query = QueryParser.parse(text);

                for (int i = 0; i < 3; i++) {
                    service.search(query); // warm-up, not counted
                }

                double[] times = new double[RUNS];
                for (int i = 0; i < RUNS; i++) {
                    long start = System.nanoTime();
                    service.search(query);
                    times[i] = (System.nanoTime() - start) / 1_000_000.0;
                }

                double avg = Arrays.stream(times).average().orElse(0);
                double max = Arrays.stream(times).max().orElse(0);
                System.out.printf("%-10d %-40s %10.2f %10.2f%n", size, text, avg, max);
            }
            System.out.println();
        }
    }

    private static InMemoryLogSearchService fill(int count) {
        Random random = new Random(42); // fixed seed, so the data is the same every run
        InMemoryLogSearchService service = new InMemoryLogSearchService(count);
        Instant now = Instant.now();
        for (int i = 0; i < count; i++) {
            Instant time = now.minusSeconds(random.nextInt(3600)); // spread over the last hour
            service.add(new Logentry(
                    String.valueOf(i), time,
                    LEVELS[random.nextInt(LEVELS.length)],
                    SERVICES[random.nextInt(SERVICES.length)],
                    MESSAGES[random.nextInt(MESSAGES.length)]));
        }
        return service;
    }
}