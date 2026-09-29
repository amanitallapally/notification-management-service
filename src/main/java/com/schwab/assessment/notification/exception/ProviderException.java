package com.schwab.assessment.notification.exception;

import com.schwab.assessment.notification.domain.FailureType;

/**
 * Thrown by {@link com.schwab.assessment.notification.service.provider.NotificationProvider}
 * implementations. Carries an explicit {@link FailureType} so callers never
 * need to inspect provider-specific exception types (requirement 4.5).
 */
public class ProviderException extends RuntimeException {

    private final FailureType failureType;

    public ProviderException(FailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public ProviderException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
