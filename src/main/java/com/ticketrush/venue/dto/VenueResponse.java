package com.ticketrush.venue.dto;

import java.time.OffsetDateTime;

public record VenueResponse(
    Long id,
    String name,
    String city,
    String layoutJson,
    OffsetDateTime createdAt
) {}
