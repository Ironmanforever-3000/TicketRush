package com.ticketrush.show.dto;

import com.ticketrush.show.ShowStatus;
import java.time.OffsetDateTime;

public record ShowResponse(
    Long id,
    Long eventId,
    String eventTitle,
    Long venueId,
    String venueName,
    OffsetDateTime startsAt,
    OffsetDateTime saleOpensAt,
    OffsetDateTime saleClosesAt,
    ShowStatus status
) {}
