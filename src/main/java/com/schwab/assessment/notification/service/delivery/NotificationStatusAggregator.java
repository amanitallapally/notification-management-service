package com.schwab.assessment.notification.service.delivery;

import com.schwab.assessment.notification.domain.DeliveryStatus;
import com.schwab.assessment.notification.domain.NotificationStatus;
import com.schwab.assessment.notification.model.NotificationEntity;
import com.schwab.assessment.notification.model.RecipientChannelEntity;
import com.schwab.assessment.notification.repository.NotificationRepository;
import com.schwab.assessment.notification.repository.RecipientChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Recomputes the overall {@link NotificationStatus} from the status of its
 * child recipient-channels. Documented as the custom state-model rollup
 * permitted by requirement 4.2 (see memory-bank/state-model.md).
 */
@Service
public class NotificationStatusAggregator {

    private static final Logger log = LoggerFactory.getLogger(NotificationStatusAggregator.class);

    private static final Set<DeliveryStatus> TERMINAL_FAILURE = Set.of(
            DeliveryStatus.FAILED_TERMINAL, DeliveryStatus.EXHAUSTED);
    private static final Set<DeliveryStatus> IN_FLIGHT = Set.of(
            DeliveryStatus.PENDING, DeliveryStatus.QUEUED, DeliveryStatus.ATTEMPTING, DeliveryStatus.FAILED_RETRYABLE);

    private final NotificationRepository notificationRepository;
    private final RecipientChannelRepository recipientChannelRepository;

    public NotificationStatusAggregator(NotificationRepository notificationRepository,
                                         RecipientChannelRepository recipientChannelRepository) {
        this.notificationRepository = notificationRepository;
        this.recipientChannelRepository = recipientChannelRepository;
    }

    @Transactional
    public void recompute(String notificationId) {
        // Pessimistic write lock serializes concurrent recomputes for the same
        // notification so a transaction never commits a rollup computed from a
        // stale sibling snapshot (see memory-bank/decisions.md).
        NotificationEntity notification = notificationRepository.findByIdForUpdate(notificationId).orElse(null);
        if (notification == null) {
            return;
        }
        List<RecipientChannelEntity> children = recipientChannelRepository.findByNotificationId(notificationId);
        if (children.isEmpty()) {
            return;
        }

        boolean anyInFlight = children.stream().anyMatch(c -> IN_FLIGHT.contains(c.getStatus()));
        boolean anySucceeded = children.stream().anyMatch(c -> c.getStatus() == DeliveryStatus.SUCCEEDED);
        boolean anyTerminalFailure = children.stream().anyMatch(c -> TERMINAL_FAILURE.contains(c.getStatus()));
        boolean allSucceeded = children.stream().allMatch(c -> c.getStatus() == DeliveryStatus.SUCCEEDED);
        boolean allTerminalFailure = children.stream().allMatch(c -> TERMINAL_FAILURE.contains(c.getStatus()));

        NotificationStatus newStatus;
        if (allSucceeded) {
            newStatus = NotificationStatus.DELIVERED;
        } else if (allTerminalFailure) {
            newStatus = NotificationStatus.FAILED;
        } else if (anyInFlight) {
            newStatus = NotificationStatus.IN_PROGRESS;
        } else if (anySucceeded && anyTerminalFailure) {
            newStatus = NotificationStatus.PARTIALLY_DELIVERED;
        } else {
            newStatus = NotificationStatus.IN_PROGRESS;
        }

        if (newStatus != notification.getStatus()) {
            log.debug("notification={} overallStatus {} -> {}", notificationId, notification.getStatus(), newStatus);
            notification.setStatus(newStatus);
            notificationRepository.save(notification);
        }
    }
}
