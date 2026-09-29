package com.schwab.assessment.notification.api.dto;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.NotificationType;
import com.schwab.assessment.notification.domain.Priority;
import com.schwab.assessment.notification.domain.Severity;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Inbound notification submission payload (requirement 4.1).
 */
public record NotificationRequest(

        @NotBlank @Size(max = 100) String sourceSystem,

        @NotBlank @Size(max = 100) String eventId,

        @NotNull NotificationType notificationType,

        @NotNull Severity severity,

        @NotNull Priority priority,

        @NotEmpty @Size(max = 100) List<@NotNull String> recipients,

        @NotEmpty List<@NotNull ChannelType> requestedChannels,

        /**
         * Client-supplied idempotency key. If omitted, one is derived from
         * sourceSystem+eventId (see memory-bank/decisions.md#deduplication).
         */
        @Size(max = 200) String idempotencyKey,

        @Size(max = 255) String subject,

        @Future Instant scheduledAt,

        @Future Instant expiresAt
) {
}
