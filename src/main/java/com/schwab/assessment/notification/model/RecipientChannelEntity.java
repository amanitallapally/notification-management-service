package com.schwab.assessment.notification.model;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One (recipient, channel) routing target for a notification. This is the unit
 * that owns a delivery status and its history of attempts.
 */
@Entity
@Table(name = "recipient_channels", indexes = {
        @Index(name = "idx_recipient_channel_notification", columnList = "notification_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientChannelEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private NotificationEntity notification;

    @Column(nullable = false)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attemptCount;

    private Instant queuedAt;
    private Instant lastAttemptAt;
    private Instant deliveredAt;
    private Instant nextRetryAt;

    /** Human-readable reason for the current status; never contains message content or secrets. */
    private String lastFailureReason;

    @Builder.Default
    @OneToMany(mappedBy = "recipientChannel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DeliveryAttemptEntity> attempts = new ArrayList<>();

    public static String newId() {
        return "rc_" + UUID.randomUUID();
    }
}
