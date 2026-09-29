package com.schwab.assessment.notification.api.dto;

import com.schwab.assessment.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Response for the status API (requirement 4.2): overall status, selected
 * channels, per recipient+channel delivery status, and relevant timestamps.
 */
public record NotificationStatusResponse(
        String notificationId,
        NotificationStatus overallStatus,
        String sourceSystem,
        String eventId,
        Instant createdAt,
        Instant scheduledAt,
        Instant expiresAt,
        /** Distinct channels selected across all recipients (requirement 4.2). */
        List<String> selectedChannels,
        List<RecipientChannelStatus> recipientChannels
) {
    public record RecipientChannelStatus(
            String recipientId,
            String channel,
            String deliveryStatus,
            int attemptCount,
            Instant queuedAt,
            Instant lastAttemptAt,
            Instant deliveredAt,
            Instant nextRetryAt,
            String lastFailureReason
    ) {
    }
}
