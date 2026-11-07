package com.ticketrush.ratelimit;

import org.springframework.http.HttpMethod;

public record RouteRateLimitRule(
    HttpMethod method,
    String pathPattern,
    String policyName
) {}
