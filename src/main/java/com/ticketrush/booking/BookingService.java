package com.ticketrush.booking;

import java.util.List;
import java.util.UUID;

public interface BookingService {

    BookingCreationResult createBooking(
        Long userId,
        UUID idempotencyKey,
        BookingRequest request
    );

    List<BookingResponse> getUserBookings(Long userId);

    BookingResponse getBookingById(Long userId, Long bookingId, boolean isAdmin);

    BookingResponse cancelBooking(Long userId, Long bookingId, boolean isAdmin);
}
