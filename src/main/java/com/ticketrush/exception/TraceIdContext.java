package com.ticketrush.exception;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Provides access to the current HTTP request's correlated trace ID stored in SLF4J MDC.
 */
public final class TraceIdContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    private TraceIdContext() {
    }

    public static String currentOrNew() {
        String existing = MDC.get(MDC_KEY);
        return (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
    }
}
