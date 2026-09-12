package com.ticketrush.hold.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record HoldResponse(
        Long holdId,
        Long showId,
        Long userId,
        List<Long> seatIds,
        OffsetDateTime expiresAt,
        String status
) {}
