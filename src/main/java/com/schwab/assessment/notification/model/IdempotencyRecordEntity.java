package com.schwab.assessment.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Dedup boundary for submission-level idempotency (requirement 4.4).
 * The key is scoped per source system to avoid cross-tenant collisions, and
 * records are retained for {@code notification.idempotency.retention-hours}
 * before becoming eligible for cleanup (see memory-bank/decisions.md).
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecordEntity {

    @Id
    private String idempotencyKey;

    @Column(nullable = false)
    private String notificationId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
