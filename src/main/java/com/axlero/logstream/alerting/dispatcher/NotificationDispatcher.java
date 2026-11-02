package com.axlero.logstream.alerting.dispatcher;

import com.axlero.logstream.alerting.model.AlertBreachEvent;
import com.axlero.logstream.alerting.model.AlertRule;
import com.axlero.logstream.alerting.model.NotificationDeliveryRecord;

/**
 * Dispatcher interface for notifying external systems when an alert threshold is breached.
 */
public interface NotificationDispatcher {

    /**
     * Determines whether this dispatcher handles the given rule configuration.
     */
    boolean supports(AlertRule rule);

    /**
     * Dispatches notification for an alert breach.
     *
     * @param breach The breach event details
     * @param rule   The rule configuration
     * @return Delivery record representing the result of the dispatch
     */
    NotificationDeliveryRecord dispatch(AlertBreachEvent breach, AlertRule rule);

    /**
     * Channel identifier (e.g. "WEBHOOK", "EMAIL").
     */
    String getChannelName();
}
