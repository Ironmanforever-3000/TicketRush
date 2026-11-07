package com.ticketrush.ratelimit;

public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds, long resetAt) {}
