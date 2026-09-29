package com.schwab.assessment.notification.service.delivery;

import com.schwab.assessment.notification.domain.DeliveryStatus;
import com.schwab.assessment.notification.model.RecipientChannelEntity;
import com.schwab.assessment.notification.repository.RecipientChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls for recipient-channels whose backoff window has elapsed (or that
 * were queued but never dispatched) and resubmits them to the delivery
 * pipeline. Standing in for a durable delayed-queue in this in-process
 * prototype (see AsyncConfig trade-off notes).
 *
 * Dispatch is deliberately synchronous ({@code DeliveryOrchestrator.process},
 * not {@code processAsync}): the atomic claim means calling it twice for the
 * same still-in-flight row is harmless, but resubmitting to the bounded
 * async executor on every poll tick before the previous task even runs would
 * pile up duplicate no-op tasks and could starve new deliveries under
 * backlog. Running synchronously on the scheduler thread trades a slightly
 * slower poll loop under heavy retry volume for that safety.
 */
@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final RecipientChannelRepository recipientChannelRepository;
    private final DeliveryOrchestrator deliveryOrchestrator;
    private final Duration staleQueueThreshold;

    public RetryScheduler(RecipientChannelRepository recipientChannelRepository,
                           DeliveryOrchestrator deliveryOrchestrator,
                           @Value("${notification.retry.stale-queue-threshold-ms:5000}") long staleQueueThresholdMs) {
        this.recipientChannelRepository = recipientChannelRepository;
        this.deliveryOrchestrator = deliveryOrchestrator;
        this.staleQueueThreshold = Duration.ofMillis(staleQueueThresholdMs);
    }

    @Scheduled(fixedDelayString = "${notification.retry.poll-interval-ms:1000}")
    public void pollDueRetries() {
        List<RecipientChannelEntity> due = recipientChannelRepository
                .findByStatusAndNextRetryAtBefore(DeliveryStatus.FAILED_RETRYABLE, Instant.now());
        for (RecipientChannelEntity item : due) {
            log.debug("Resubmitting due retry recipientChannel={}", item.getId());
            deliveryOrchestrator.process(item.getId());
        }

        List<RecipientChannelEntity> stale = recipientChannelRepository
                .findByStatusAndQueuedAtBefore(DeliveryStatus.QUEUED, Instant.now().minus(staleQueueThreshold));
        for (RecipientChannelEntity item : stale) {
            log.warn("Recovering stale QUEUED recipientChannel={} (never dispatched)", item.getId());
            deliveryOrchestrator.process(item.getId());
        }
    }
}

