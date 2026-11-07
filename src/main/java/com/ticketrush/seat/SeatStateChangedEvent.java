package com.ticketrush.seat;

import java.time.Instant;
import java.util.List;

public record SeatStateChangedEvent(
    Long eventId,          // == outbox row's own id
    Long showId,
    List<Long> seatIds,
    String status,          // AVAILABLE | HELD | SOLD
    Long version,           // intentionally equal to eventId; kept as a separate field for payload clarity
    Instant occurredAt
) {}
