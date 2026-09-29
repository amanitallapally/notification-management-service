package com.schwab.assessment.notification.domain;

/**
 * Status of a single delivery attempt for one recipient on one channel.
 */
public enum DeliveryStatus {
    PENDING,
    QUEUED,
    ATTEMPTING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    SUPPRESSED_DUPLICATE,
    EXHAUSTED
}
