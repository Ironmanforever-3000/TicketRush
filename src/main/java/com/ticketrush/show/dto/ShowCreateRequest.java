package com.ticketrush.show.dto;

import com.ticketrush.show.ShowStatus;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ShowCreateRequest(
    @NotNull Long eventId,
    @NotNull Long venueId,
    @NotNull OffsetDateTime startsAt,
    @NotNull OffsetDateTime saleOpensAt,
    OffsetDateTime saleClosesAt,
    @NotNull ShowStatus status
) {}
