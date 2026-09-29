package com.schwab.assessment.notification.service.delivery;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Thin async entry point for the delivery pipeline. Delegates the actual
 * transactional work to {@link DeliveryAttemptExecutor} so that bean's
 * {@code @Transactional} methods run behind their own Spring proxy. Runs on
 * the {@code deliveryExecutor} pool so the submission API returns
 * immediately (requirement: asynchronous processing).
 */
@Service
public class DeliveryOrchestrator {

    private final DeliveryAttemptExecutor deliveryAttemptExecutor;

    public DeliveryOrchestrator(DeliveryAttemptExecutor deliveryAttemptExecutor) {
        this.deliveryAttemptExecutor = deliveryAttemptExecutor;
    }

    @Async("deliveryExecutor")
    public void processAsync(String recipientChannelId) {
        deliveryAttemptExecutor.attempt(recipientChannelId);
    }

    /** Synchronous variant used directly by the retry scheduler and tests. */
    public void process(String recipientChannelId) {
        deliveryAttemptExecutor.attempt(recipientChannelId);
    }
}

