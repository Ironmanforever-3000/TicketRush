package com.ticketrush.seat.dto;

import java.util.List;

public record SeatMapResponse(
    Long showId,
    List<SeatResponse> seats
) {}
