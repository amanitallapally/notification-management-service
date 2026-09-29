package com.schwab.assessment.notification.repository;

import com.schwab.assessment.notification.model.RecipientChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RecipientChannelRepository extends JpaRepository<RecipientChannelEntity, String> {

    List<RecipientChannelEntity> findByNotificationId(String notificationId);

    /**
     * Atomically claims a recipient-channel for an attempt: only rows that
     * are still QUEUED or FAILED_RETRYABLE are moved to ATTEMPTING. This is
     * the delivery-level dedup boundary (requirement 4.4 bullet 2): if two
     * threads race to reprocess the same queued item, only one update
     * affects a row, so only one performs the side-effecting provider call.
     */
    @Modifying
    @Query("update RecipientChannelEntity r set r.status = com.schwab.assessment.notification.domain.DeliveryStatus.ATTEMPTING, " +
            "r.attemptCount = r.attemptCount + 1, r.lastAttemptAt = :now " +
            "where r.id = :id and r.status in (com.schwab.assessment.notification.domain.DeliveryStatus.QUEUED, " +
            "com.schwab.assessment.notification.domain.DeliveryStatus.FAILED_RETRYABLE)")
    int claimForAttempt(@Param("id") String id, @Param("now") Instant now);

    List<RecipientChannelEntity> findByStatusAndNextRetryAtBefore(
            com.schwab.assessment.notification.domain.DeliveryStatus status, Instant before);
}
