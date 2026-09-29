package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.model.IdempotencyRecordEntity;
import com.schwab.assessment.notification.repository.IdempotencyRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Submission-level dedup boundary (requirement 4.4).
 *
 * Boundary: one idempotency key maps to exactly one logical notification,
 * scoped for the retention window below. A repeat submission with the same
 * key within the window returns the original notification id instead of
 * creating a new one.
 *
 * Retention policy: keys are retained for
 * {@code notification.idempotency.retention-hours} (default 24h), matching
 * the typical lifetime of a notification and its retries. After expiry the
 * key may be reused; this is a deliberate trade-off between unbounded
 * storage growth and dedup safety (see memory-bank/decisions.md).
 */
@Service
public class DeduplicationService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final Duration retention;

    public DeduplicationService(IdempotencyRecordRepository idempotencyRecordRepository,
                                 @Value("${notification.idempotency.retention-hours:24}") long retentionHours) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.retention = Duration.ofHours(retentionHours);
    }

    /**
     * Returns the existing notification id if this key was already accepted
     * (and is still within its retention window), otherwise reserves the key
     * for the given notification and returns empty.
     */
    public Optional<String> checkAndReserve(String idempotencyKey, String notificationId) {
        Instant now = Instant.now();
        Optional<IdempotencyRecordEntity> existing =
                idempotencyRecordRepository.findByIdempotencyKeyAndExpiresAtAfter(idempotencyKey, now);
        if (existing.isPresent()) {
            return Optional.of(existing.get().getNotificationId());
        }

        try {
            idempotencyRecordRepository.save(IdempotencyRecordEntity.builder()
                    .idempotencyKey(idempotencyKey)
                    .notificationId(notificationId)
                    .createdAt(now)
                    .expiresAt(now.plus(retention))
                    .build());
            return Optional.empty();
        } catch (DataIntegrityViolationException raceLost) {
            // Concurrent submission won the race for this key; treat as duplicate.
            return idempotencyRecordRepository.findByIdempotencyKeyAndExpiresAtAfter(idempotencyKey, now)
                    .map(IdempotencyRecordEntity::getNotificationId);
        }
    }
}
