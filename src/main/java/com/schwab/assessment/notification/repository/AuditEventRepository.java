package com.schwab.assessment.notification.repository;

import com.schwab.assessment.notification.model.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {
    List<AuditEventEntity> findByNotificationIdOrderByOccurredAtAsc(String notificationId);
}
