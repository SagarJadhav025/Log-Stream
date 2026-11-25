# 🛠️ Log Stream — Operations & Production Runbook

## 1. Configuration Reference (`application.properties`)

| Property | Default | Description |
| :--- | :--- | :--- |
| `grpc.server.port` | `9090` | gRPC ingestion server port |
| `logstream.index-dir` | `./lucene-index` | On-disk Lucene index directory |
| `logstream.lucene.ram-buffer-mb` | `256` | Lucene `IndexWriter` RAM buffer before segment flush |
| `logstream.lucene.refresh-interval-ms` | `1000` | NRT `SearcherManager` soft refresh interval |
| `logstream.lucene.commit-interval-seconds` | `30` | Periodic durable disk commit interval |
| `logstream.lucene.max-merged-segment-mb` | `512` | `TieredMergePolicy` max merged segment size |
| `logstream.lucene.segments-per-tier` | `10` | `TieredMergePolicy` segments per tier |
| `logstream.ingestion.buffer-capacity` | `100000` | Bounded `ArrayBlockingQueue` capacity |
| `logstream.ingestion.batch-size` | `2000` | Max documents per Lucene `indexBatch()` call |
| `logstream.ingestion.flush-interval-ms` | `50` | Max wait before flushing partial batch |
| `logstream.ingestion.websocket-sample-rate` | `10` | Forward 1-in-N batches to WebSocket live tail |
| `spring.threads.virtual.enabled` | `true` | Enable Java 21 Virtual Threads |

---

## 2. Graceful Shutdown
- `AsyncLogBatchProcessor` (`@PreDestroy`) sets `running = false`, stops accepting new logs, and drains all remaining buffered entries into Lucene before shutdown.
- `LogIndexer` (`@PreDestroy`) shuts down background NRT refresh/commit schedulers, closes `SearcherManager`, and commits + closes `IndexWriter`.

---

## 3. Troubleshooting & Edge-Case Protections

### Slow Search Queries (`HTTP 408`)
- `SearchApiController` enforces a **5,000 ms** timeout circuit-breaker on Lucene queries.
- If a wildcard or broad query exceeds 5s, it is cancelled and `HTTP 408 Request Timeout` is returned to protect backend threads.

### Buffer Saturation Warnings
- Look for `Ingestion buffer saturated! Dropped log entries so far: N` in logs.
- **Remediation:** Increase `logstream.ingestion.buffer-capacity` (requires more JVM heap) or `logstream.ingestion.batch-size`.

### Slow WebSocket Consumers
- `LiveTailWebSocketHandler` caps each browser session queue at **1,000 messages** and samples `INFO`/`DEBUG` at 1-in-5 while always forwarding `ERROR`/`WARN`.
- Slow clients automatically drop oldest/excess frames instead of exhausting server heap.

---

## 4. Docker Deployment
```bash
# Build and start backend + frontend
docker compose up --build -d

# View backend logs
docker compose logs -f backend

# Stop and preserve lucene-index volume
docker compose down
```
