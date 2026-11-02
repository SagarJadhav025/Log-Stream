package com.axlero.logstream.alerting.controller;

import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import com.axlero.logstream.alerting.service.AlertManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * REST API for configuring alert rules, triggering tests, inspecting history,
 * and testing webhook payloads.
 */
@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "http://localhost:3000")
public class AlertApiController {

    private static final Logger logger = LoggerFactory.getLogger(AlertApiController.class);

    private final AlertManagerService alertManagerService;

    public AlertApiController(AlertManagerService alertManagerService) {
        this.alertManagerService = alertManagerService;
    }

    @GetMapping("/rules")
    public Collection<AlertRule> getRules() {
        return alertManagerService.getAllRules();
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<AlertRule> getRule(@PathVariable String id) {
        return alertManagerService.getRule(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    public ResponseEntity<AlertRule> saveRule(@RequestBody AlertRule rule) {
        AlertRule saved = alertManagerService.saveRule(rule);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable String id) {
        boolean deleted = alertManagerService.deleteRule(id);
        return ResponseEntity.ok(Map.of("deleted", deleted, "ruleId", id));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<List<AlertBreachEvent>> evaluateAll() {
        List<AlertBreachEvent> breaches = alertManagerService.evaluateAll();
        return ResponseEntity.ok(breaches);
    }

    @PostMapping("/evaluate/{id}")
    public ResponseEntity<?> evaluateRule(
            @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean bypassCooldown) {
        var breachOpt = alertManagerService.evaluateRule(id, bypassCooldown);
        if (breachOpt.isPresent()) {
            return ResponseEntity.ok(breachOpt.get());
        }
        return ResponseEntity.ok(Map.of("status", "NO_BREACH_OR_IN_COOLDOWN", "ruleId", id));
    }

    @PostMapping("/test-webhook")
    public ResponseEntity<NotificationDeliveryRecord> testWebhook(@RequestBody Map<String, String> body) {
        String url = body.get("webhookUrl");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        NotificationDeliveryRecord result = alertManagerService.testWebhook(url);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<List<NotificationDeliveryRecord>> getHistory() {
        return ResponseEntity.ok(alertManagerService.getDeliveryHistory());
    }

    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory() {
        alertManagerService.clearDeliveryHistory();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    /**
     * Local webhook sink for testing and verifying webhook payloads locally.
     */
    @PostMapping("/webhook-sink")
    public ResponseEntity<Map<String, Object>> webhookSink(@RequestBody Map<String, Object> payload) {
        logger.info("Local Webhook Sink received payload: {}", payload);
        return ResponseEntity.ok(Map.of("status", "received", "event", payload.getOrDefault("event", "UNKNOWN")));
    }
}
