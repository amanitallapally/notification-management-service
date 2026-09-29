package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.api.dto.AuditEventResponse;
import com.schwab.assessment.notification.api.dto.NotificationStatusResponse;
import com.schwab.assessment.notification.exception.NotificationNotFoundException;
import com.schwab.assessment.notification.model.NotificationEntity;
import com.schwab.assessment.notification.repository.AuditEventRepository;
import com.schwab.assessment.notification.repository.NotificationRepository;
import com.schwab.assessment.notification.repository.RecipientChannelRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serves requirement 4.2 (status retrieval).
 */
@Service
public class NotificationStatusService {

    private final NotificationRepository notificationRepository;
    private final RecipientChannelRepository recipientChannelRepository;
    private final AuditEventRepository auditEventRepository;

    public NotificationStatusService(NotificationRepository notificationRepository,
                                      RecipientChannelRepository recipientChannelRepository,
                                      AuditEventRepository auditEventRepository) {
        this.notificationRepository = notificationRepository;
        this.recipientChannelRepository = recipientChannelRepository;
        this.auditEventRepository = auditEventRepository;
    }

    public NotificationStatusResponse getStatus(String notificationId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        List<NotificationStatusResponse.RecipientChannelStatus> recipientChannels =
                recipientChannelRepository.findByNotificationId(notificationId).stream()
                        .map(rc -> new NotificationStatusResponse.RecipientChannelStatus(
                                rc.getRecipientId(),
                                rc.getChannel().name(),
                                rc.getStatus().name(),
                                rc.getAttemptCount(),
                                rc.getQueuedAt(),
                                rc.getLastAttemptAt(),
                                rc.getDeliveredAt(),
                                rc.getNextRetryAt(),
                                rc.getLastFailureReason()))
                        .toList();

        return new NotificationStatusResponse(
                notification.getId(),
                notification.getStatus(),
                notification.getSourceSystem(),
                notification.getEventId(),
                notification.getCreatedAt(),
                notification.getScheduledAt(),
                notification.getExpiresAt(),
                recipientChannels);
    }

    public List<AuditEventResponse> getAuditHistory(String notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new NotificationNotFoundException(notificationId);
        }
        return auditEventRepository.findByNotificationIdOrderByOccurredAtAsc(notificationId).stream()
                .map(e -> new AuditEventResponse(e.getAction().name(), e.getDetails(), e.getOccurredAt()))
                .toList();
    }
}
