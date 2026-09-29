package com.schwab.assessment.notification.repository;

import com.schwab.assessment.notification.model.DeliveryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, String> {
    List<DeliveryAttemptEntity> findByRecipientChannelIdOrderByAttemptNumberAsc(String recipientChannelId);
}
