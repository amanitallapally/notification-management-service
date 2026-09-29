package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.domain.AuditAction;
import com.schwab.assessment.notification.model.AuditEventEntity;
import com.schwab.assessment.notification.repository.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Records the audit trail required by 4.9. Deliberately takes only
 * identifiers and short context strings - never raw message bodies,
 * recipient contact details, or provider credentials.
 */
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void record(String notificationId, AuditAction action, String details) {
        AuditEventEntity event = AuditEventEntity.builder()
                .id(AuditEventEntity.newId())
                .notificationId(notificationId)
                .action(action)
                .details(details)
                .occurredAt(Instant.now())
                .build();
        auditEventRepository.save(event);
    }
}
