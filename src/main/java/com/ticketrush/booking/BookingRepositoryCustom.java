package com.ticketrush.booking;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepositoryCustom {

    Optional<Long> insertIfAbsent(
        Long userId,
        Long showId,
        Long holdId,
        Long totalCents,
        UUID idempotencyKey
    );
}
