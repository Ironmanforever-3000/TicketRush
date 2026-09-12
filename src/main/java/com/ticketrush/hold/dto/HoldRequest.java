package com.ticketrush.hold.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HoldRequest(
        @NotNull(message = "User ID is required")
        Long userId,

        @NotEmpty(message = "At least one seat ID is required")
        List<@NotNull Long> seatIds
) {}
