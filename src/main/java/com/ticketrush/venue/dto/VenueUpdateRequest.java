package com.ticketrush.venue.dto;

import jakarta.validation.constraints.Size;

public record VenueUpdateRequest(
    @Size(max = 150) String name,
    @Size(max = 100) String city,
    String layoutJson
) {}
