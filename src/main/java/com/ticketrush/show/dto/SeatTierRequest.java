package com.ticketrush.show.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SeatTierRequest(
    @NotBlank String name,
    @NotNull @Positive Long priceCents,
    @NotBlank String currency
) {}
