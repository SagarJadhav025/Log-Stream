
package com.axlero.logstream.alerting.controller;

import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.AlertSeverity;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import com.axlero.logstream.alerting.service.AlertManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AlertApiControllerTest {

    private AlertManagerService alertManagerService;
    private MockMvc mockMvc;

    private AlertRule sampleRule;
    private AlertBreachEvent sampleBreach;
    private NotificationDeliveryRecord sampleDelivery;

    @BeforeEach
    void setUp() {
        alertManagerService = mock(AlertManagerService.class);

        AlertApiController controller =
                new AlertApiController(alertManagerService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        sampleRule = new AlertRule(
                "rule-1",
                "High error rate",
                "level:ERROR",
                10,
                5,
                5,
                "https://example.com/webhook",
                null,
                AlertSeverity.CRITICAL,
                true
        );

        sampleBreach = new AlertBreachEvent(
                "rule-1",
                "High error rate",
                "level:ERROR",
                10,
                15,
                5,
                Instant.parse("2026-11-24T10:00:00Z"),
                AlertSeverity.CRITICAL,
                List.of("Payment failed")
        );

        sampleDelivery = NotificationDeliveryRecord.of(
                "rule-1",
                "WEBHOOK",
                "https://example.com/webhook",
                true,
                200,
                "Delivered"
        );
    }

    @Test
    void returnsAllRules() throws Exception {
        when(alertManagerService.getAllRules())
                .thenReturn(List.of(sampleRule));

        mockMvc.perform(get("/api/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("rule-1"))
                .andExpect(jsonPath("$[0].name").value("High error rate"));

        verify(alertManagerService).getAllRules();
    }

    @Test
    void returnsRuleWhenFound() throws Exception {
        when(alertManagerService.getRule("rule-1"))
                .thenReturn(Optional.of(sampleRule));

        mockMvc.perform(get("/api/alerts/rules/rule-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rule-1"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"));
    }

    @Test
    void returnsNotFoundWhenRuleDoesNotExist() throws Exception {
        when(alertManagerService.getRule("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/alerts/rules/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void savesAndReturnsRule() throws Exception {
        when(alertManagerService.saveRule(any(AlertRule.class)))
                .thenReturn(sampleRule);

        mockMvc.perform(post("/api/alerts/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "rule-1",
                                  "name": "High error rate",
                                  "query": "level:ERROR",
                                  "threshold": 10,
                                  "windowMinutes": 5,
                                  "cooldownMinutes": 5,
                                  "webhookUrl": "https://example.com/webhook",
                                  "severity": "CRITICAL",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rule-1"))
                .andExpect(jsonPath("$.threshold").value(10));

        verify(alertManagerService).saveRule(any(AlertRule.class));
    }

    @Test
    void deletesRuleAndReturnsDeletionStatus() throws Exception {
        when(alertManagerService.deleteRule("rule-1"))
                .thenReturn(true);

        mockMvc.perform(delete("/api/alerts/rules/rule-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.ruleId").value("rule-1"));
    }

    @Test
    void returnsFalseWhenRuleDeletionFails() throws Exception {
        when(alertManagerService.deleteRule("missing"))
                .thenReturn(false);

        mockMvc.perform(delete("/api/alerts/rules/missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    void evaluatesAllRules() throws Exception {
        when(alertManagerService.evaluateAll())
                .thenReturn(List.of(sampleBreach));

        mockMvc.perform(post("/api/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleId").value("rule-1"))
                .andExpect(jsonPath("$[0].actualCount").value(15));
    }

    @Test
    void returnsBreachWhenRuleEvaluationSucceeds() throws Exception {
        when(alertManagerService.evaluateRule("rule-1", true))
                .thenReturn(Optional.of(sampleBreach));

        mockMvc.perform(post("/api/alerts/evaluate/rule-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").value("rule-1"))
                .andExpect(jsonPath("$.actualCount").value(15));

        verify(alertManagerService).evaluateRule("rule-1", true);
    }

    @Test
    void returnsNoBreachStatusWhenRuleDoesNotBreach() throws Exception {
        when(alertManagerService.evaluateRule("rule-1", false))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/alerts/evaluate/rule-1")
                        .param("bypassCooldown", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("NO_BREACH_OR_IN_COOLDOWN"))
                .andExpect(jsonPath("$.ruleId").value("rule-1"));
    }

    @Test
    void rejectsWebhookWhenUrlIsMissing() throws Exception {
        mockMvc.perform(post("/api/alerts/test-webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(alertManagerService, never()).testWebhook(anyString());
    }

    @Test
    void rejectsWebhookWhenUrlIsBlank() throws Exception {
        mockMvc.perform(post("/api/alerts/test-webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"webhookUrl": "   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testsWebhookWhenUrlIsValid() throws Exception {
        when(alertManagerService.testWebhook(
                "https://example.com/webhook"))
                .thenReturn(sampleDelivery);

        mockMvc.perform(post("/api/alerts/test-webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"webhookUrl": "https://example.com/webhook"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    void returnsDeliveryHistory() throws Exception {
        when(alertManagerService.getDeliveryHistory())
                .thenReturn(List.of(sampleDelivery));

        mockMvc.perform(get("/api/alerts/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleId").value("rule-1"))
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    void clearsDeliveryHistory() throws Exception {
        mockMvc.perform(delete("/api/alerts/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cleared"));

        verify(alertManagerService).clearDeliveryHistory();
    }

    @Test
    void webhookSinkReturnsProvidedEvent() throws Exception {
        mockMvc.perform(post("/api/alerts/webhook-sink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event": "ALERT_TRIGGERED", "ruleId": "rule-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.event").value("ALERT_TRIGGERED"));
    }

    @Test
    void webhookSinkUsesUnknownWhenEventIsMissing() throws Exception {
        mockMvc.perform(post("/api/alerts/webhook-sink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleId": "rule-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event").value("UNKNOWN"));
    }
}
