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

import java.util.List;
import java.util.Optional;

/**
 * Handles requirement 4.1 (submit) end to end: bean validation happens at
 * the controller layer; this service owns idempotency, routing/persistence
 * delegation, and kicking off asynchronous delivery.
 */
@Service
public class NotificationSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSubmissionService.class);

    private final NotificationRepository notificationRepository;
    private final DeduplicationService deduplicationService;
    private final AuditService auditService;
    private final NotificationPersistenceService persistenceService;
    private final DeliveryOrchestrator deliveryOrchestrator;

    public NotificationSubmissionService(NotificationRepository notificationRepository,
                                          DeduplicationService deduplicationService,
                                          AuditService auditService,
                                          NotificationPersistenceService persistenceService,
                                          DeliveryOrchestrator deliveryOrchestrator) {
        this.notificationRepository = notificationRepository;
        this.deduplicationService = deduplicationService;
        this.auditService = auditService;
        this.persistenceService = persistenceService;
        this.deliveryOrchestrator = deliveryOrchestrator;
    }

    public NotificationSubmissionResponse submit(NotificationRequest request) {
        String notificationId = NotificationEntity.newId();
        String idempotencyKey = resolveIdempotencyKey(request);

        Optional<String> existing = deduplicationService.checkAndReserve(idempotencyKey, notificationId);
        if (existing.isPresent()) {
            String originalId = existing.get();
            log.info("Suppressed duplicate submission idempotencyKey={} originalNotificationId={}", idempotencyKey, originalId);
            auditService.record(originalId, AuditAction.NOTIFICATION_DUPLICATE_SUPPRESSED,
                    "idempotencyKey=" + idempotencyKey);
            NotificationStatus status = notificationRepository.findById(originalId)
                    .map(NotificationEntity::getStatus)
                    .orElse(NotificationStatus.DUPLICATE);
            return new NotificationSubmissionResponse(originalId, status, true);
        }

        NotificationEntity notification = persistenceService.persistNotification(notificationId, idempotencyKey, request);
        log.info("Accepted notification={} sourceSystem={} eventId={}", notificationId, request.sourceSystem(), request.eventId());
        auditService.record(notificationId, AuditAction.NOTIFICATION_ACCEPTED,
                "sourceSystem=" + request.sourceSystem() + ",eventId=" + request.eventId());

        List<RecipientChannelEntity> targets = persistenceService.routeAndQueue(notification, request);
        log.debug("Queued {} recipient-channel targets for notification={}", targets.size(), notificationId);
        targets.forEach(target -> deliveryOrchestrator.processAsync(target.getId()));

        return new NotificationSubmissionResponse(notificationId, notification.getStatus(), false);
    }

    private String resolveIdempotencyKey(NotificationRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            return request.idempotencyKey();
        }
        // Documented assumption (ambiguous requirement scenario): when the caller
        // does not supply an explicit key, derive one from sourceSystem+eventId so
        // that resubmissions of the same upstream event are still deduplicated.
        return request.sourceSystem() + ":" + request.eventId();
    }
}

