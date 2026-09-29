package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.model.IdempotencyRecordEntity;
import com.schwab.assessment.notification.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private DeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        deduplicationService = new DeduplicationService(idempotencyRecordRepository, 24);
    }

    @Test
    void firstSubmissionReservesKeyAndReturnsEmpty() {
        when(idempotencyRecordRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any()))
                .thenReturn(Optional.empty());

        Optional<String> result = deduplicationService.checkAndReserve("key-1", "ntf_1");

        assertThat(result).isEmpty();
    }

    @Test
    void repeatSubmissionWithSameKeyReturnsOriginalNotificationId() {
        IdempotencyRecordEntity existing = IdempotencyRecordEntity.builder()
                .idempotencyKey("key-1")
                .notificationId("ntf_original")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(idempotencyRecordRepository.findByIdempotencyKeyAndExpiresAtAfter(any(), any()))
                .thenReturn(Optional.of(existing));

        Optional<String> result = deduplicationService.checkAndReserve("key-1", "ntf_new");

        assertThat(result).contains("ntf_original");
    }
}
