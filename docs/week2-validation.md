# Week 2 Validation Results (Mehrajfathima782)

Dataset: 1,000,000 mock logs (seeded Random(42), 5 services, 6 levels, last 60 minutes).
Machine: Windows, Java 25, single machine, default JVM settings.

## Throughput audit (target: 10,000 logs/sec)

INDEXED 1000000 logs in 15.0s (66803 logs/sec) - PASS

## Search validation (target: under 50 ms)

Query: message:timeout, top 50 results

| Run | Time |
|---|---|
| 1 (cold) | 23.84 ms |
| 2 | 4.58 ms |
| 3 | 1.74 ms |
| 4 | 1.88 ms |
| 5 | 1.86 ms |

Warmed-up search is about 1.8 ms - PASS

## Notes

- Indexing is single-threaded with a 256 MB RAM buffer and one commit at the end.
- Even the cold first query (23.84 ms) is under 50 ms.
- Run with: mvnw spring-boot:run -Dspring-boot.run.arguments=--logstream.seed=true
