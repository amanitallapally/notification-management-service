package com.schwab.assessment.notification.domain;

/**
 * Overall lifecycle status of a notification (not a per-channel delivery status).
 * Custom state model documented in memory-bank/state-model.md.
 */
public enum NotificationStatus {
    RECEIVED,
    ROUTED,
    QUEUED,
    IN_PROGRESS,
    DELIVERED,
    PARTIALLY_DELIVERED,
    FAILED,
    REJECTED,
    DUPLICATE,
    EXPIRED,
    CANCELLED
}
