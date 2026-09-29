package com.schwab.assessment.notification.api.dto;

import com.schwab.assessment.notification.domain.NotificationStatus;

public record NotificationSubmissionResponse(
        String notificationId,
        NotificationStatus status,
        boolean duplicate
) {
}
