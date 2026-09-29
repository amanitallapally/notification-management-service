package com.schwab.assessment.notification.model;

import com.schwab.assessment.notification.domain.FailureType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a single delivery attempt made by a provider. Attempts
 * are append-only; retries create new rows rather than mutating history.
 */
@Entity
@Table(name = "delivery_attempts", indexes = {
        @Index(name = "idx_attempt_recipient_channel", columnList = "recipient_channel_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAttemptEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_channel_id", nullable = false)
    private RecipientChannelEntity recipientChannel;

    @Column(nullable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    private FailureType failureType;

    private String providerName;

    /** Non-sensitive provider response summary (e.g. HTTP status, error code). */
    private String providerResponseSummary;

    @Column(nullable = false)
    private Instant attemptedAt;

    public static String newId() {
        return "att_" + UUID.randomUUID();
    }
}
