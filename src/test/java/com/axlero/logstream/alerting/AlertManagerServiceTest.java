package com.axlero.logstream.alerting;

import com.axlero.logstream.alerting.dispatcher.NotificationDispatcher;
import com.axlero.logstream.alerting.engine.RuleEngine;
import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.AlertSeverity;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;
import com.axlero.logstream.alerting.service.AlertManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AlertManagerServiceTest {

    private AlertManagerService service;
    private List<NotificationDeliveryRecord> testDispatched;

    @BeforeEach
    void setUp() {
        testDispatched = new ArrayList<>();

        RuleEngine mockEngine = new RuleEngine(null) {
            @Override
            public Optional<AlertBreachEvent> evaluate(AlertRule rule) {
                if ("trigger-rule".equals(rule.id())) {
                    return Optional.of(new AlertBreachEvent(
                            rule.id(),
                            rule.name(),
                            rule.query(),
                            rule.threshold(),
                            20,
                            rule.windowMinutes(),
                            Instant.now(),
                            rule.severity(),
                            List.of("mock log line")
                    ));
                }
                return Optional.empty();
            }
        };

        NotificationDispatcher testDispatcher = new NotificationDispatcher() {
            @Override
            public boolean supports(AlertRule rule) {
                return rule.webhookUrl() != null;
            }

            @Override
            public NotificationDeliveryRecord dispatch(AlertBreachEvent breach, AlertRule rule) {
                NotificationDeliveryRecord rec = NotificationDeliveryRecord.of(
                        rule.id(), "WEBHOOK", rule.webhookUrl(), true, 200, "Delivered"
                );
                testDispatched.add(rec);
                return rec;
            }

            @Override
            public String getChannelName() {
                return "WEBHOOK";
            }
        };

        service = new AlertManagerService(mockEngine, List.of(testDispatcher));
    }

    @Test
    void savesAndRetrievesRules() {
        AlertRule rule = AlertRule.of("r100", "Custom Rule", "level:WARN", 2, 5, "simulated://url");
        service.saveRule(rule);

        Optional<AlertRule> found = service.getRule("r100");
        assertTrue(found.isPresent());
        assertEquals("Custom Rule", found.get().name());

        assertTrue(service.deleteRule("r100"));
        assertTrue(service.getRule("r100").isEmpty());
    }

    @Test
    void evaluatesRulesAndEnforcesCooldown() {
        AlertRule rule = AlertRule.of("trigger-rule", "Spike Alert", "level:ERROR", 5, 5, "simulated://url");
        service.saveRule(rule);

        // First evaluation: breach should be detected and dispatched
        List<AlertBreachEvent> breaches1 = service.evaluateAll();
        assertEquals(1, breaches1.size());
        assertEquals(1, testDispatched.size());
        assertEquals(1, service.getDeliveryHistory().size());

        // Second evaluation immediately after: rule is in cooldown, so no breach or dispatch occurs
        List<AlertBreachEvent> breaches2 = service.evaluateAll();
        assertEquals(0, breaches2.size());
        assertEquals(1, testDispatched.size());

        // But manual evaluation with bypassCooldown=true executes
        Optional<AlertBreachEvent> manualBreach = service.evaluateRule("trigger-rule", true);
        assertTrue(manualBreach.isPresent());
        assertEquals(2, testDispatched.size());
    }

    @Test
    void continuesDispatchingWhenOneDispatcherThrows() {
        AlertRule rule = AlertRule.of(
                "failure-test",
                "Dispatcher Failure Test",
                "level:ERROR",
                1,
                5,
                "simulated://webhook"
        );

        AlertBreachEvent breach = new AlertBreachEvent(
                rule.id(),
                rule.name(),
                rule.query(),
                rule.threshold(),
                10,
                rule.windowMinutes(),
                Instant.now(),
                rule.severity(),
                List.of("Test error log")
        );

        NotificationDispatcher failingDispatcher = new NotificationDispatcher() {
            @Override
            public boolean supports(AlertRule rule) {
                return true;
            }

            @Override
            public NotificationDeliveryRecord dispatch(
                    AlertBreachEvent breach, AlertRule rule) {
                throw new RuntimeException("Simulated email failure");
            }

            @Override
            public String getChannelName() {
                return "EMAIL";
            }
        };

        NotificationDispatcher successfulDispatcher = new NotificationDispatcher() {
            @Override
            public boolean supports(AlertRule rule) {
                return true;
            }

            @Override
            public NotificationDeliveryRecord dispatch(
                    AlertBreachEvent breach, AlertRule rule) {
                return NotificationDeliveryRecord.of(
                        rule.id(),
                        "WEBHOOK",
                        rule.webhookUrl(),
                        true,
                        200,
                        "Delivered"
                );
            }

            @Override
            public String getChannelName() {
                return "WEBHOOK";
            }
        };

        AlertManagerService testService = new AlertManagerService(
                null,
                List.of(failingDispatcher, successfulDispatcher)
        );

        List<NotificationDeliveryRecord> records =
                testService.dispatchNotifications(breach, rule);

        assertEquals(2, records.size());
        assertFalse(records.get(0).success());
        assertEquals("EMAIL", records.get(0).channel());

        assertTrue(records.get(1).success());
        assertEquals("WEBHOOK", records.get(1).channel());

        assertEquals(2, testService.getDeliveryHistory().size());
    }
    @Test
    void preventsDuplicateAlertsDuringConcurrentEvaluation() throws Exception {
        AtomicInteger evaluationCount = new AtomicInteger();
        AtomicInteger dispatchCount = new AtomicInteger();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch evaluationStarted = new CountDownLatch(1);
        CountDownLatch releaseEvaluation = new CountDownLatch(1);

        AlertBreachEvent breach = new AlertBreachEvent(
                "concurrent-rule",
                "Concurrent Evaluation Test",
                "level:ERROR",
                1,
                10,
                5,
                Instant.now(),
                AlertSeverity.CRITICAL,
                List.of("Test error log")
        );

        RuleEngine controlledEngine = new RuleEngine(null) {
            @Override
            public Optional<AlertBreachEvent> evaluate(AlertRule rule) {
                evaluationCount.incrementAndGet();
                evaluationStarted.countDown();

                try {
                    if (!releaseEvaluation.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to release rule evaluation"
                        );
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }

                return Optional.of(breach);
            }
        };

        NotificationDispatcher testDispatcher = new NotificationDispatcher() {
            @Override
            public boolean supports(AlertRule rule) {
                return true;
            }

            @Override
            public NotificationDeliveryRecord dispatch(
                    AlertBreachEvent event, AlertRule rule) {
                dispatchCount.incrementAndGet();

                return NotificationDeliveryRecord.of(
                        rule.id(),
                        "WEBHOOK",
                        rule.webhookUrl(),
                        true,
                        200,
                        "Delivered"
                );
            }

            @Override
            public String getChannelName() {
                return "WEBHOOK";
            }
        };

        AlertManagerService concurrentService = new AlertManagerService(
                controlledEngine,
                List.of(testDispatcher)
        );

        AlertRule rule = AlertRule.of(
                "concurrent-rule",
                "Concurrent Evaluation Test",
                "level:ERROR",
                1,
                5,
                "simulated://concurrent-test"
        );

        concurrentService.saveRule(rule);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<AlertBreachEvent>> first =
                    executor.submit(() -> {
                        callersReady.countDown();
                        startGate.await();
                        return concurrentService.evaluateRule(
                                "concurrent-rule", false
                        );
                    });

            Future<Optional<AlertBreachEvent>> second =
                    executor.submit(() -> {
                        callersReady.countDown();
                        startGate.await();
                        return concurrentService.evaluateRule(
                                "concurrent-rule", false
                        );
                    });

            assertTrue(
                    callersReady.await(5, TimeUnit.SECONDS),
                    "Both evaluation threads should be ready"
            );

            startGate.countDown();

            assertTrue(
                    evaluationStarted.await(5, TimeUnit.SECONDS),
                    "One thread should enter rule evaluation"
            );

            releaseEvaluation.countDown();

            Optional<AlertBreachEvent> firstResult =
                    first.get(5, TimeUnit.SECONDS);
            Optional<AlertBreachEvent> secondResult =
                    second.get(5, TimeUnit.SECONDS);

            assertTrue(
                    firstResult.isPresent() ^ secondResult.isPresent(),
                    "Exactly one concurrent evaluation should produce a breach"
            );

            assertEquals(
                    1,
                    evaluationCount.get(),
                    "The rule engine should evaluate the rule only once"
            );

            assertEquals(
                    1,
                    dispatchCount.get(),
                    "Only one notification should be dispatched"
            );

            assertEquals(
                    1,
                    concurrentService.getDeliveryHistory().size(),
                    "Only one delivery record should be created"
            );
        } finally {
            releaseEvaluation.countDown();
            executor.shutdownNow();
        }
    }

}
