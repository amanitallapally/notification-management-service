package com.schwab.assessment.notification.api.dto;

import java.time.Instant;

public record AuditEventResponse(
        String action,
        String details,
        Instant occurredAt
) {
}
