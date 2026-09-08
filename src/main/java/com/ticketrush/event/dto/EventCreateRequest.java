package com.ticketrush.event.dto;

import com.ticketrush.event.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventCreateRequest(
    @NotNull Long organizerId,
    @NotBlank @Size(max = 200) String title,
    String description,
    @NotBlank @Size(max = 100) String category,
    @NotNull EventStatus status
) {}
