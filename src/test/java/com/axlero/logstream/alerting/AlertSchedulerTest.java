package com.axlero.logstream.alerting;

import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.scheduler.AlertScheduler;
import com.axlero.logstream.alerting.service.AlertManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class AlertSchedulerTest {

    private AlertManagerService alertManagerService;
    private AlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        alertManagerService = mock(AlertManagerService.class);
        scheduler = new AlertScheduler(alertManagerService);
    }

    @Test
    void invokesAlertEvaluation() {
        when(alertManagerService.evaluateAll()).thenReturn(List.of());

        scheduler.runScheduledAlertEvaluation();

        verify(alertManagerService, times(1)).evaluateAll();
    }

    @Test
    void handlesDetectedBreaches() {
        AlertBreachEvent breach = mock(AlertBreachEvent.class);

        when(alertManagerService.evaluateAll()).thenReturn(List.of(breach));

        assertDoesNotThrow(scheduler::runScheduledAlertEvaluation);

        verify(alertManagerService, times(1)).evaluateAll();
    }

    @Test
    void handlesEvaluationExceptions() {
        when(alertManagerService.evaluateAll())
                .thenThrow(new RuntimeException("Simulated evaluation failure"));

        assertDoesNotThrow(scheduler::runScheduledAlertEvaluation);

        verify(alertManagerService, times(1)).evaluateAll();
    }
}
