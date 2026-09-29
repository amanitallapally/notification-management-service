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

    public NotificationPersistenceService(NotificationRepository notificationRepository,
                                           RecipientChannelRepository recipientChannelRepository,
                                           RoutingService routingService,
                                           AuditService auditService) {
        this.notificationRepository = notificationRepository;
        this.recipientChannelRepository = recipientChannelRepository;
        this.routingService = routingService;
        this.auditService = auditService;
    }

    @Transactional
    public NotificationEntity persistNotification(String notificationId, String idempotencyKey, NotificationRequest request) {
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

    @Transactional
    public List<RecipientChannelEntity> routeAndQueue(NotificationEntity notification, NotificationRequest request) {
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
}
