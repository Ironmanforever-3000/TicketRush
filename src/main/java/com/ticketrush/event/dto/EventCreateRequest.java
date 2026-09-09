package com.ticketrush.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EventCreateRequest(

        @NotNull
        @Positive
        Long organizerId,

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 5000)
        String description,

        @NotBlank
        @Size(max = 100)
        String category
) {
}
