package com.ticketrush.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BookingRequest(
    @NotNull
    Long showId,

    @NotEmpty
    @Size(min = 1, max = 10)
    List<@NotNull Long> seatIds
) {
}
