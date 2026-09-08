package com.ticketrush.seat.dto;

import com.ticketrush.seat.SeatStatus;

public record SeatResponse(
    Long id,
    String rowLabel,
    Integer seatNumber,
    SeatStatus status,
    String tierName,
    Long priceCents,
    String currency
) {}
