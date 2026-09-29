package com.schwab.assessment.notification.domain;

/**
 * Significant, auditable actions per requirement 4.9. No message bodies or
 * credentials are ever stored alongside these events.
 */
public enum AuditAction {
    NOTIFICATION_ACCEPTED,
    NOTIFICATION_REJECTED,
    NOTIFICATION_DUPLICATE_SUPPRESSED,
    ROUTING_DECISION_MADE,
    DELIVERY_QUEUED,
    DELIVERY_ATTEMPTED,
    DELIVERY_SUCCEEDED,
    DELIVERY_FAILED,
    RETRY_SCHEDULED,
    RETRY_EXHAUSTED,
    NOTIFICATION_EXPIRED,
    NOTIFICATION_CANCELLED
}
