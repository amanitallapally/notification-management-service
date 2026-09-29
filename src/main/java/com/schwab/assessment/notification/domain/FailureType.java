package com.schwab.assessment.notification.domain;

/**
 * Classification of provider/delivery failures, used to decide retry eligibility.
 */
public enum FailureType {
    TRANSIENT_PROVIDER_FAILURE(true),
    TIMEOUT(true),
    RATE_LIMITED(true),
    PERMANENT_PROVIDER_REJECTION(false),
    INVALID_RECIPIENT(false),
    AUTH_ERROR(false),
    UNKNOWN(true);

    private final boolean retryable;

    FailureType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
