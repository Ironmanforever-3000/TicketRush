package com.ticketrush.ratelimit;

public record RateLimitPolicy(
    String name,
    long capacity,
    double refillRate,
    long requestCost,
    IdentityStrategy identityStrategy
) {
    public RateLimitPolicy(String name, long capacity, double refillRate, long requestCost) {
        this(name, capacity, refillRate, requestCost, IdentityStrategy.USER_OR_IP);
    }
}
