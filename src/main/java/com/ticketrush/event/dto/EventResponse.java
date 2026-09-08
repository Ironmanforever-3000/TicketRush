package com.ticketrush.event.dto;

import com.ticketrush.event.EventStatus;
import java.time.OffsetDateTime;

public record EventResponse(
    Long id,
    Long organizerId,
    String title,
    String description,
    String category,
    EventStatus status,
    OffsetDateTime createdAt
) {}
