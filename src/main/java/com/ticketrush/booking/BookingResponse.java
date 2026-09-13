package com.ticketrush.booking;

import java.util.List;

public record BookingResponse(
    Long bookingId,
    Long showId,
    List<Long> seatIds,
    Long totalCents,
    String status
) {
}
