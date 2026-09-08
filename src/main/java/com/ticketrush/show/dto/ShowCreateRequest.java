package com.ticketrush.show.dto;

import com.ticketrush.show.ShowStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record ShowCreateRequest(
    @NotNull Long eventId,
    @NotNull Long venueId,
    @NotNull OffsetDateTime startsAt,
    @NotNull OffsetDateTime saleOpensAt,
    OffsetDateTime saleClosesAt,
    @NotNull ShowStatus status,
    @NotEmpty @Valid List<SeatTierRequest> tiers
) {}
