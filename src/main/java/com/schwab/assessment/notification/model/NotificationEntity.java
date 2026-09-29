package com.schwab.assessment.notification.model;

import com.schwab.assessment.notification.domain.NotificationStatus;
import com.schwab.assessment.notification.domain.NotificationType;
import com.schwab.assessment.notification.domain.Priority;
import com.schwab.assessment.notification.domain.Severity;
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

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_idempotency_key", columnList = "idempotencyKey", unique = true),
        @Index(name = "idx_notification_source_event", columnList = "sourceSystem,eventId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String sourceSystem;

    /** Event or correlation identifier supplied by the source system. */
    @Column(nullable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    /**
     * Idempotency key used for the dedup boundary described in
     * memory-bank/decisions.md#deduplication. Unique per logical notification.
     */
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant scheduledAt;

    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    /** Short, non-sensitive subject/description. The raw message body is not persisted. */
    private String subject;

    @Builder.Default
    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecipientChannelEntity> recipientChannels = new ArrayList<>();

    public static String newId() {
        return "ntf_" + UUID.randomUUID();
    }
}
