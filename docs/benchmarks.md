# 📊 Log Stream — Benchmark Report & Methodology

## 1. Test Environment & JVM Configuration
- **Runtime:** Java 21+ (Virtual Threads enabled via `spring.threads.virtual.enabled=true`)
- **Garbage Collector (Production Docker):** Generational ZGC (`-XX:+UseZGC -XX:+ZGenerational`, `-Xms512m -Xmx2g`)
- **Lucene Engine:** Apache Lucene 10.5.2 with `TieredMergePolicy` (`maxMergedSegmentMB=512`, `segmentsPerTier=10`, `ramBufferSizeMB=256`)

---

## 2. Ingestion Stress Test (`LogIngestionStressTest`)
Simulates 10 concurrent gRPC streaming clients pushing 100,000 log entries each (**1,000,000 total logs**) to `localhost:9090`.

- **Pipeline Path:** `gRPC StreamLogs` → `AsyncLogBatchProcessor` (100,000-entry `ArrayBlockingQueue`, 2,000-doc micro-batches, 50ms flush interval) → `LogIndexer.indexBatch()`
- **Target:** `>= 10,000 logs/sec` sustained throughput with zero OOM or gRPC worker blocking.
- **How to run:**
  Start the backend (`mvn spring-boot:run`), then run `LogIngestionStressTest.main()` from your IDE or `mvn test-compile exec:java -Dexec.mainClass="com.axlero.logstream.benchmark.LogIngestionStressTest" -Dexec.classpathScope=test`.

---

## 3. Search Latency Benchmark (`SearchLatencyBenchmarkTest`)
Measures **p50** and **p95** query latency on a 100,000-document in-memory Lucene index after 5 JVM JIT warmup rounds across 30 measurement iterations per query type.

| Benchmark Case | Query Pattern | SLA Target (p95) |
| :--- | :--- | :--- |
| `termSearchP95Under50ms` | Free-text term (`"timeout"`, limit 50) | `<= 50 ms` |
| `levelFilterSearchP95Under50ms` | Exact level filter (`level=ERROR`, limit 100) | `<= 50 ms` |
| `serviceFilterSearchP95Under50ms` | Exact service filter (`service=auth-service`, limit 100) | `<= 50 ms` |
| `combinedFilterSearchP95Under50ms` | Combined (`"database"` + `ERROR` + `payment-gw`, limit 50) | `<= 50 ms` |

- **How to run:**
  ```bash
  mvn test "-Dtest=SearchLatencyBenchmarkTest"
  ```

---

## 4. NRT & Concurrency Verification (`LuceneNrtSearcherManagerTest`)
- Verifies that newly indexed batches become searchable immediately after `maybeRefresh()` without waiting for a 30-second disk `commit()`.
- Stress-tests 10 concurrent reader threads performing 500 total `acquireSearcher()` / `releaseSearcher()` cycles with zero reader leaks or `AlreadyClosedException`.
