package com.ticketrush.admin.dto;

import java.time.OffsetDateTime;

public record AdminHealthResponse(
        String status,
        String database,
        String redis,
        long activeHolds,
        OffsetDateTime checkedAt
) {
}
