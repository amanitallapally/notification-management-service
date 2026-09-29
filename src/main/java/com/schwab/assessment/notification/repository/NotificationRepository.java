package com.schwab.assessment.notification.repository;

import com.schwab.assessment.notification.model.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {
    Optional<NotificationEntity> findByIdempotencyKey(String idempotencyKey);
}
