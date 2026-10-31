
package com.logstream.search;

import com.logstream.livetail.LiveTailHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;


@RestController
public class StatsController {

    private final InMemoryLogSearchService store;
    private final LiveTailHandler liveTail;
    private final Instant startedAt = Instant.now(); // when the app started

    public StatsController(InMemoryLogSearchService store, LiveTailHandler liveTail) {
        this.store = store;
        this.liveTail = liveTail;
    }

    public record Stats(int liveTailClients, int storedLogs, long uptimeSeconds) {
    }

    @GetMapping("/api/stats")
    public Stats stats() {
        long uptime = Duration.between(startedAt, Instant.now()).getSeconds();
        return new Stats(liveTail.clientCount(), store.size(), uptime);
    }
}