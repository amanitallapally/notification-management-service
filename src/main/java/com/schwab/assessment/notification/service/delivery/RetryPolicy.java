package com.schwab.assessment.notification.service.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Bounded exponential-backoff retry policy (requirement 4.5).
 * maxAttempts includes the initial attempt, e.g. maxAttempts=5 means up to
 * 4 retries after the first failure.
 */
@Component
public class RetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final long maxBackoffMs;

    public RetryPolicy(@Value("${notification.retry.max-attempts:5}") int maxAttempts,
                        @Value("${notification.retry.initial-backoff-ms:1000}") long initialBackoffMs,
                        @Value("${notification.retry.backoff-multiplier:2.0}") double backoffMultiplier,
                        @Value("${notification.retry.max-backoff-ms:30000}") long maxBackoffMs) {
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffMs = maxBackoffMs;
    }

    public boolean isExhausted(int attemptCount) {
        return attemptCount >= maxAttempts;
    }

    public Instant nextRetryAt(int attemptCount) {
        double backoff = initialBackoffMs * Math.pow(backoffMultiplier, Math.max(0, attemptCount - 1));
        long backoffMs = (long) Math.min(backoff, maxBackoffMs);
        return Instant.now().plus(Duration.ofMillis(backoffMs));
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
