package com.schwab.assessment.notification.repository;

import com.schwab.assessment.notification.model.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {
    Optional<IdempotencyRecordEntity> findByIdempotencyKeyAndExpiresAtAfter(String idempotencyKey, Instant now);
}
