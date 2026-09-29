package com.schwab.assessment.notification.model;

import com.schwab.assessment.notification.domain.AuditAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail entry (requirement 4.9). Deliberately excludes
 * message bodies, recipient contact details, and credentials - only
 * identifiers and decision metadata are stored.
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_notification", columnList = "notificationId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    /** Optional context, e.g. "channel=EMAIL,recipient=r1,reason=TIMEOUT". */
    private String details;

    @Column(nullable = false)
    private Instant occurredAt;

    public static String newId() {
        return "aud_" + UUID.randomUUID();
    }
}
