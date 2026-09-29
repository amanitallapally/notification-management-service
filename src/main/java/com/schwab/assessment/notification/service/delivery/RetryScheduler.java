package com.schwab.assessment.notification.service.delivery;

import com.schwab.assessment.notification.domain.DeliveryStatus;
import com.schwab.assessment.notification.model.RecipientChannelEntity;
import com.schwab.assessment.notification.repository.RecipientChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Polls for recipient-channels whose backoff window has elapsed and
 * resubmits them to the delivery pipeline. Standing in for a durable
 * delayed-queue in this in-process prototype (see AsyncConfig trade-off
 * notes).
 */
@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final RecipientChannelRepository recipientChannelRepository;
    private final DeliveryOrchestrator deliveryOrchestrator;

    public RetryScheduler(RecipientChannelRepository recipientChannelRepository,
                           DeliveryOrchestrator deliveryOrchestrator) {
        this.recipientChannelRepository = recipientChannelRepository;
        this.deliveryOrchestrator = deliveryOrchestrator;
    }

    @Scheduled(fixedDelayString = "${notification.retry.poll-interval-ms:1000}")
    public void pollDueRetries() {
        List<RecipientChannelEntity> due = recipientChannelRepository
                .findByStatusAndNextRetryAtBefore(DeliveryStatus.FAILED_RETRYABLE, Instant.now());
        for (RecipientChannelEntity item : due) {
            log.debug("Resubmitting due retry recipientChannel={}", item.getId());
            deliveryOrchestrator.processAsync(item.getId());
        }
    }
}
