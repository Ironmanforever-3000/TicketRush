package com.ticketrush.event.dto;

import com.ticketrush.event.EventStatus;
import jakarta.validation.constraints.Size;

public record EventUpdateRequest(
    @Size(max = 200) String title,
    String description,
    @Size(max = 100) String category,
    EventStatus status
) {}
