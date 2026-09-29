package com.schwab.assessment.notification.service.delivery;

import com.schwab.assessment.notification.domain.AuditAction;
import com.schwab.assessment.notification.domain.DeliveryStatus;
import com.schwab.assessment.notification.domain.FailureType;
import com.schwab.assessment.notification.exception.ProviderException;
import com.schwab.assessment.notification.model.DeliveryAttemptEntity;
import com.schwab.assessment.notification.model.NotificationEntity;
import com.schwab.assessment.notification.model.RecipientChannelEntity;
import com.schwab.assessment.notification.repository.DeliveryAttemptRepository;
import com.schwab.assessment.notification.repository.RecipientChannelRepository;
import com.schwab.assessment.notification.service.AuditService;
import com.schwab.assessment.notification.service.provider.NotificationProvider;
import com.schwab.assessment.notification.service.provider.ProviderDeliveryRequest;
import com.schwab.assessment.notification.service.provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Owns the transactional core of a single delivery attempt: claim, provider
 * call, and result persistence. Kept as its own bean (rather than methods on
 * {@link DeliveryOrchestrator}) so {@code @Transactional} is honored via the
 * Spring proxy - self-invocation from an {@code @Async} method on the same
 * class would silently skip the proxy.
 */
@Service
public class DeliveryAttemptExecutor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAttemptExecutor.class);

    private final RecipientChannelRepository recipientChannelRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final ProviderRegistry providerRegistry;
    private final RetryPolicy retryPolicy;
    private final AuditService auditService;
    private final NotificationStatusAggregator statusAggregator;

    public DeliveryAttemptExecutor(RecipientChannelRepository recipientChannelRepository,
                                    DeliveryAttemptRepository deliveryAttemptRepository,
                                    ProviderRegistry providerRegistry,
                                    RetryPolicy retryPolicy,
                                    AuditService auditService,
                                    NotificationStatusAggregator statusAggregator) {
        this.recipientChannelRepository = recipientChannelRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.providerRegistry = providerRegistry;
        this.retryPolicy = retryPolicy;
        this.auditService = auditService;
        this.statusAggregator = statusAggregator;
    }

    /**
     * Claims the row atomically so reprocessing the same queued item never
     * performs the provider call twice (requirement 4.4). The whole attempt
     * (claim + simulated provider call + result persist) runs in one
     * transaction here because the provider calls in this prototype are
     * fast in-memory simulations. A production system would keep the
     * outbound provider I/O call outside the DB transaction boundary (see
     * memory-bank/decisions.md).
     */
    @Transactional
    public void attempt(String recipientChannelId) {
        int claimed = recipientChannelRepository.claimForAttempt(recipientChannelId, Instant.now());
        if (claimed == 0) {
            log.debug("recipientChannel={} already claimed or not eligible; skipping", recipientChannelId);
            return;
        }

        RecipientChannelEntity recipientChannel = recipientChannelRepository.findById(recipientChannelId)
                .orElseThrow(() -> new IllegalStateException("Claimed row disappeared: " + recipientChannelId));
        NotificationEntity notification = recipientChannel.getNotification();

        auditService.record(notification.getId(), AuditAction.DELIVERY_ATTEMPTED,
                "channel=" + recipientChannel.getChannel() + ",recipient=" + recipientChannel.getRecipientId()
                        + ",attempt=" + recipientChannel.getAttemptCount());

        NotificationProvider provider = providerRegistry.resolve(recipientChannel.getChannel());
        ProviderDeliveryRequest request = new ProviderDeliveryRequest(
                notification.getId(),
                recipientChannel.getRecipientId(),
                notification.getSubject(),
                notification.getSeverity(),
                notification.getNotificationType(),
                recipientChannel.getAttemptCount());

        try {
            String summary = provider.send(request);
            handleSuccess(recipientChannel, provider.channel().name(), summary);
        } catch (ProviderException ex) {
            handleFailure(recipientChannel, provider.channel().name(), ex);
        } catch (Exception unexpected) {
            log.error("Unclassified provider error for recipientChannel={}", recipientChannelId, unexpected);
            handleFailure(recipientChannel, provider.channel().name(),
                    new ProviderException(FailureType.UNKNOWN, "Unclassified error: " + unexpected.getClass().getSimpleName()));
        }

        statusAggregator.recompute(notification.getId());
    }

    private void handleSuccess(RecipientChannelEntity recipientChannel, String providerName, String summary) {
        recipientChannel.setStatus(DeliveryStatus.SUCCEEDED);
        recipientChannel.setDeliveredAt(Instant.now());
        recipientChannel.setLastFailureReason(null);
        recipientChannelRepository.save(recipientChannel);

        deliveryAttemptRepository.save(DeliveryAttemptEntity.builder()
                .id(DeliveryAttemptEntity.newId())
                .recipientChannel(recipientChannel)
                .attemptNumber(recipientChannel.getAttemptCount())
                .success(true)
                .providerName(providerName)
                .providerResponseSummary(summary)
                .attemptedAt(Instant.now())
                .build());

        auditService.record(recipientChannel.getNotification().getId(), AuditAction.DELIVERY_SUCCEEDED,
                "channel=" + recipientChannel.getChannel() + ",recipient=" + recipientChannel.getRecipientId());
    }

    private void handleFailure(RecipientChannelEntity recipientChannel, String providerName, ProviderException ex) {
        deliveryAttemptRepository.save(DeliveryAttemptEntity.builder()
                .id(DeliveryAttemptEntity.newId())
                .recipientChannel(recipientChannel)
                .attemptNumber(recipientChannel.getAttemptCount())
                .success(false)
                .failureType(ex.getFailureType())
                .providerName(providerName)
                .providerResponseSummary(ex.getMessage())
                .attemptedAt(Instant.now())
                .build());

        boolean canRetry = ex.getFailureType().isRetryable() && !retryPolicy.isExhausted(recipientChannel.getAttemptCount());

        if (canRetry) {
            recipientChannel.setStatus(DeliveryStatus.FAILED_RETRYABLE);
            recipientChannel.setNextRetryAt(retryPolicy.nextRetryAt(recipientChannel.getAttemptCount()));
            recipientChannel.setLastFailureReason(ex.getFailureType() + ": " + ex.getMessage());
            recipientChannelRepository.save(recipientChannel);

            auditService.record(recipientChannel.getNotification().getId(), AuditAction.DELIVERY_FAILED,
                    "channel=" + recipientChannel.getChannel() + ",reason=" + ex.getFailureType());
            auditService.record(recipientChannel.getNotification().getId(), AuditAction.RETRY_SCHEDULED,
                    "channel=" + recipientChannel.getChannel() + ",nextRetryAt=" + recipientChannel.getNextRetryAt());
        } else {
            recipientChannel.setStatus(ex.getFailureType().isRetryable() ? DeliveryStatus.EXHAUSTED : DeliveryStatus.FAILED_TERMINAL);
            recipientChannel.setLastFailureReason(ex.getFailureType() + ": " + ex.getMessage());
            recipientChannelRepository.save(recipientChannel);

            auditService.record(recipientChannel.getNotification().getId(), AuditAction.DELIVERY_FAILED,
                    "channel=" + recipientChannel.getChannel() + ",reason=" + ex.getFailureType() + ",terminal=true");
            if (ex.getFailureType().isRetryable()) {
                auditService.record(recipientChannel.getNotification().getId(), AuditAction.RETRY_EXHAUSTED,
                        "channel=" + recipientChannel.getChannel());
            }
        }
    }
}
