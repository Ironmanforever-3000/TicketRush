package com.ticketrush.booking;

public record BookingCreationResult(
    BookingResponse response,
    boolean created
) {
}
