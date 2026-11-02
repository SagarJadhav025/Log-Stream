package com.axlero.logstream.alerting.scheduler;

import com.axlero.logstream.alerting.service.AlertManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically executes alert rule evaluations against the Lucene index.
 */
@Component
public class AlertScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AlertScheduler.class);

    private final AlertManagerService alertManagerService;

    public AlertScheduler(AlertManagerService alertManagerService) {
        this.alertManagerService = alertManagerService;
    }

    @Scheduled(fixedDelayString = "${logstream.alerting.schedule-ms:15000}", initialDelay = 10000)
    public void runScheduledAlertEvaluation() {
        try {
            var breaches = alertManagerService.evaluateAll();
            if (!breaches.isEmpty()) {
                logger.info("Scheduled alert scan completed: {} breach(es) detected and dispatched.", breaches.size());
            }
        } catch (Exception e) {
            logger.error("Error during scheduled alert evaluation: {}", e.getMessage(), e);
        }
    }
}
