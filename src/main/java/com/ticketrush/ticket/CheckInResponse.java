package com.ticketrush.ticket;

import java.time.OffsetDateTime;

public record CheckInResponse(
        CheckInResult result,
        String message,
        String seat,
        String tier,
        OffsetDateTime checkedInAt
) {}
