package com.ticketrush.exception;

import java.util.Map;

public record ErrorResponse(
    ErrorDetail error
) {
    public record ErrorDetail(
        String code,
        String message,
        Map<String, String> details,
        String traceId
    ) {}
}
