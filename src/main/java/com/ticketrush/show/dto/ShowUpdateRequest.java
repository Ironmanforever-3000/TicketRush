package com.ticketrush.show.dto;

import com.ticketrush.show.ShowStatus;
import java.time.OffsetDateTime;

public record ShowUpdateRequest(
    OffsetDateTime startsAt,
    OffsetDateTime saleOpensAt,
    OffsetDateTime saleClosesAt,
    ShowStatus status
) {}
