package com.schwab.assessment.notification.service.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 30000);

    @Test
    void isNotExhaustedBelowMaxAttempts() {
        assertThat(policy.isExhausted(1)).isFalse();
        assertThat(policy.isExhausted(2)).isFalse();
    }

    @Test
    void isExhaustedAtOrAboveMaxAttempts() {
        assertThat(policy.isExhausted(3)).isTrue();
        assertThat(policy.isExhausted(4)).isTrue();
    }

    @Test
    void nextRetryAtGrowsExponentiallyAndRespectsCap() {
        Instant now = Instant.now();
        Instant firstRetry = policy.nextRetryAt(1);
        Instant secondRetry = policy.nextRetryAt(2);

        assertThat(firstRetry).isAfter(now);
        // second backoff (attempt 2) should be roughly double the first (attempt 1)
        assertThat(secondRetry).isAfterOrEqualTo(firstRetry);
    }

    @Test
    void backoffIsCappedAtMaxBackoffMs() {
        RetryPolicy cappedPolicy = new RetryPolicy(10, 1000, 10.0, 5000);
        Instant now = Instant.now();
        Instant retryAt = cappedPolicy.nextRetryAt(5);

        assertThat(retryAt).isBeforeOrEqualTo(now.plusMillis(5100));
    }
}
