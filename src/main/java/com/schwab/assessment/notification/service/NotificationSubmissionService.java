package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.api.dto.NotificationRequest;
import com.schwab.assessment.notification.api.dto.NotificationSubmissionResponse;
import com.schwab.assessment.notification.domain.AuditAction;
import com.schwab.assessment.notification.domain.NotificationStatus;
import com.schwab.assessment.notification.model.NotificationEntity;
import com.schwab.assessment.notification.model.RecipientChannelEntity;
import com.schwab.assessment.notification.repository.NotificationRepository;
import com.schwab.assessment.notification.service.delivery.DeliveryOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.RejectedExecutionException;

/**
 * Handles requirement 4.1 (submit) end to end: bean validation happens at
 * the controller layer; this service owns idempotency, routing/persistence
 * delegation, and kicking off asynchronous delivery.
 */
@Service
public class NotificationSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSubmissionService.class);

    private final NotificationRepository notificationRepository;
    private final AuditService auditService;
    private final NotificationPersistenceService persistenceService;
    private final DeliveryOrchestrator deliveryOrchestrator;

    public NotificationSubmissionService(NotificationRepository notificationRepository,
                                          AuditService auditService,
                                          NotificationPersistenceService persistenceService,
                                          DeliveryOrchestrator deliveryOrchestrator) {
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
        this.persistenceService = persistenceService;
        this.deliveryOrchestrator = deliveryOrchestrator;
    }

    public NotificationSubmissionResponse submit(NotificationRequest request) {
        String notificationId = NotificationEntity.newId();
        String idempotencyKey = resolveIdempotencyKey(request);

        NotificationPersistenceService.SubmissionResult result =
                persistenceService.submitTransactionally(notificationId, idempotencyKey, request);

        if (result.duplicate()) {
            log.info("Suppressed duplicate submission idempotencyKey={} originalNotificationId={}", idempotencyKey, result.notificationId());
            auditService.record(result.notificationId(), AuditAction.NOTIFICATION_DUPLICATE_SUPPRESSED,
                    "idempotencyKey=" + idempotencyKey);
            NotificationStatus status = notificationRepository.findById(result.notificationId())
                    .map(NotificationEntity::getStatus)
                    .orElse(NotificationStatus.DUPLICATE);
            return new NotificationSubmissionResponse(result.notificationId(), status, true);
        }

        log.info("Accepted notification={} sourceSystem={} eventId={}", notificationId, request.sourceSystem(), request.eventId());
        log.debug("Queued {} recipient-channel targets for notification={}", result.targets().size(), notificationId);

        for (RecipientChannelEntity target : result.targets()) {
            try {
                deliveryOrchestrator.processAsync(target.getId());
            } catch (RejectedExecutionException rejected) {
                // The row is already persisted as QUEUED; RetryScheduler's stale-queue
                // sweep will pick it up and dispatch it, so this is not lost - just delayed.
                log.warn("Delivery executor queue full; recipientChannel={} left QUEUED for scheduler recovery", target.getId());
            }
        }

        return new NotificationSubmissionResponse(notificationId, result.notification().getStatus(), false);
    }

    private String resolveIdempotencyKey(NotificationRequest request) {
        // Always namespace by sourceSystem - both for the caller-supplied key and
        // the derived fallback - so two source systems using the same key/eventId
        // never suppress or leak each other's notifications (see
        // memory-bank/decisions.md#deduplication).
        String suffix = (request.idempotencyKey() != null && !request.idempotencyKey().isBlank())
                ? request.idempotencyKey().trim()
                : request.eventId();
        return request.sourceSystem() + ":" + suffix;
    }
}


