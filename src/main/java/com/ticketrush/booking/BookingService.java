package com.ticketrush.booking;

import java.util.UUID;

public interface BookingService {

    BookingCreationResult createBooking(
        Long userId,
        UUID idempotencyKey,
        BookingRequest request
    );
}
