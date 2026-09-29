package com.schwab.assessment.notification.service.provider;

import com.schwab.assessment.notification.domain.NotificationType;
import com.schwab.assessment.notification.domain.Severity;

/**
 * Minimal, non-sensitive payload passed to a provider. Intentionally excludes
 * raw message bodies to keep provider adapters simple for this prototype.
 */
public record ProviderDeliveryRequest(
        String notificationId,
        String recipientId,
        String subject,
        Severity severity,
        NotificationType notificationType,
        int attemptNumber
) {
}
