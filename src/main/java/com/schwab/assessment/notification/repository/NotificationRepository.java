package com.schwab.assessment.notification.repository;

import com.schwab.assessment.notification.model.NotificationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    /**
     * Fetches with a pessimistic write lock so concurrent delivery-attempt
     * transactions recomputing the same notification's rollup status
     * serialize instead of racing on a stale in-memory snapshot of the
     * children (see memory-bank/decisions.md#status-rollup-locking).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationEntity n where n.id = :id")
    Optional<NotificationEntity> findByIdForUpdate(@Param("id") String id);
}

