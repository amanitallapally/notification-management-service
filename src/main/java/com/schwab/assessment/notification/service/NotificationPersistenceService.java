package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.api.dto.NotificationRequest;
import com.schwab.assessment.notification.domain.AuditAction;
import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.DeliveryStatus;
import com.schwab.assessment.notification.domain.NotificationStatus;
import com.schwab.assessment.notification.model.NotificationEntity;
import com.schwab.assessment.notification.model.RecipientChannelEntity;
import com.schwab.assessment.notification.repository.NotificationRepository;
import com.schwab.assessment.notification.repository.RecipientChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owns the transactional persistence steps for a submission. Kept as a
 * separate bean (rather than private methods on the submission service) so
 * {@code @Transactional} is honored via the Spring proxy - self-invocation
 * from within the same class would silently skip the proxy.
 */
@Service
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;
    private final RecipientChannelRepository recipientChannelRepository;
    private final RoutingService routingService;
    private final AuditService auditService;
    private final DeduplicationService deduplicationService;

    public NotificationPersistenceService(NotificationRepository notificationRepository,
                                           RecipientChannelRepository recipientChannelRepository,
                                           RoutingService routingService,
                                           AuditService auditService,
                                           DeduplicationService deduplicationService) {
        this.notificationRepository = notificationRepository;
        this.recipientChannelRepository = recipientChannelRepository;
        this.routingService = routingService;
        this.auditService = auditService;
        this.deduplicationService = deduplicationService;
    }

    /**
     * Reserves the idempotency key, persists the notification, and routes/
     * queues its recipient-channels - all in a single transaction. Doing this
     * atomically means a failure partway through (e.g. routing throws) rolls
     * back the idempotency reservation too, so the key is never left pointing
     * at a notification that was never fully created (see
     * memory-bank/decisions.md).
     */
    @Transactional
    public SubmissionResult submitTransactionally(String notificationId, String idempotencyKey, NotificationRequest request) {
        Optional<String> existing = deduplicationService.checkAndReserve(idempotencyKey, notificationId);
        if (existing.isPresent()) {
            return SubmissionResult.duplicate(existing.get());
        }

        NotificationEntity notification = persistNotification(notificationId, idempotencyKey, request);
        auditService.record(notificationId, AuditAction.NOTIFICATION_ACCEPTED,
                "sourceSystem=" + request.sourceSystem() + ",eventId=" + request.eventId());

        List<RecipientChannelEntity> targets = routeAndQueue(notification, request);
        return SubmissionResult.created(notification, targets);
    }

    private NotificationEntity persistNotification(String notificationId, String idempotencyKey, NotificationRequest request) {
        Instant now = Instant.now();
        NotificationEntity notification = NotificationEntity.builder()
                .id(notificationId)
                .sourceSystem(request.sourceSystem())
                .eventId(request.eventId())
                .notificationType(request.notificationType())
                .severity(request.severity())
                .priority(request.priority())
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .scheduledAt(request.scheduledAt())
                .expiresAt(request.expiresAt())
                .subject(request.subject())
                .status(NotificationStatus.RECEIVED)
                .build();
        return notificationRepository.save(notification);
    }

    private List<RecipientChannelEntity> routeAndQueue(NotificationEntity notification, NotificationRequest request) {
        Instant now = Instant.now();
        List<RecipientChannelEntity> created = new ArrayList<>();

        for (String recipientId : request.recipients()) {
            List<ChannelType> resolvedChannels = routingService.resolveChannels(
                    recipientId, request.requestedChannels(), request.severity());

            auditService.record(notification.getId(), AuditAction.ROUTING_DECISION_MADE,
                    "recipient=" + recipientId + ",channels=" + resolvedChannels);

            for (ChannelType channel : resolvedChannels) {
                RecipientChannelEntity rc = RecipientChannelEntity.builder()
                        .id(RecipientChannelEntity.newId())
                        .notification(notification)
                        .recipientId(recipientId)
                        .channel(channel)
                        .status(DeliveryStatus.QUEUED)
                        .attemptCount(0)
                        .queuedAt(now)
                        .build();
                recipientChannelRepository.save(rc);
                created.add(rc);

                auditService.record(notification.getId(), AuditAction.DELIVERY_QUEUED,
                        "recipient=" + recipientId + ",channel=" + channel);
            }
        }

        notification.setStatus(NotificationStatus.ROUTED);
        notificationRepository.save(notification);
        return created;
    }

    /** Result of {@link #submitTransactionally}: either an existing duplicate or a newly created notification + its queued targets. */
    public record SubmissionResult(boolean duplicate, String notificationId, NotificationEntity notification,
                                    List<RecipientChannelEntity> targets) {

        static SubmissionResult duplicate(String originalNotificationId) {
            return new SubmissionResult(true, originalNotificationId, null, List.of());
        }

        static SubmissionResult created(NotificationEntity notification, List<RecipientChannelEntity> targets) {
            return new SubmissionResult(false, notification.getId(), notification, targets);
        }
    }
}

