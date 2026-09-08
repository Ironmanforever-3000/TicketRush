package com.ticketrush.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VenueCreateRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 100) String city,
    @NotBlank String layoutJson
) {}
