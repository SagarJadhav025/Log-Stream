package com.axlero.logstream.alerting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertNotifier notifier;
    private final List<String> received = new CopyOnWriteArrayList<>();

    public AlertController(AlertNotifier notifier) {
        this.notifier = notifier;
    }

    // POST /api/alerts/test?rule=high-errors&query=level:ERROR&count=150&threshold=100
    @PostMapping("/test")
    public Map<String, Object> test(
            @RequestParam(defaultValue = "demo-rule") String rule,
            @RequestParam(defaultValue = "level:ERROR") String query,
            @RequestParam(defaultValue = "150") long count,
            @RequestParam(defaultValue = "100") long threshold) {
        boolean delivered = notifier.dispatch(new AlertEvent(rule, query, count, threshold, Instant.now()));
        return Map.of("delivered", delivered);
    }

    // Demo webhook target so the full round trip works on one machine.
    @PostMapping("/mock-receiver")
    public Map<String, Object> mockReceiver(@RequestBody String body) {
        received.add(body);
        if (received.size() > 100) received.remove(0);
        return Map.of("received", true);
    }

    @GetMapping("/received")
    public List<String> received() {
        return received;
    }
}
