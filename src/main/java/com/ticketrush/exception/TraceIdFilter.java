package com.ticketrush.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a per-request trace ID into SLF4J MDC and echoes it via X-Trace-Id so log lines
 * and ErrorResponse.traceId values correlate cleanly across the entire request lifecycle.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(TraceIdContext.HEADER_NAME);
        String traceId = (incoming != null && !incoming.isBlank() && incoming.length() <= 64)
                ? incoming.trim()
                : UUID.randomUUID().toString();

        MDC.put(TraceIdContext.MDC_KEY, traceId);
        response.setHeader(TraceIdContext.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdContext.MDC_KEY);
        }
    }
}
