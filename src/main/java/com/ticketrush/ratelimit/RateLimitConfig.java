package com.ticketrush.ratelimit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

@Configuration
public class RateLimitConfig {

    @Bean
    public Map<String, RateLimitPolicy> rateLimitPolicies() {
        return Map.of(
            "events",   new RateLimitPolicy("events", 100, 2.0, 1, IdentityStrategy.USER_OR_IP),
            "seatmap",  new RateLimitPolicy("seatmap", 60, 1.0, 1, IdentityStrategy.USER_OR_IP),
            "holds",    new RateLimitPolicy("holds", 5, 0.1, 1, IdentityStrategy.USER),
            "bookings", new RateLimitPolicy("bookings", 5, 0.1, 1, IdentityStrategy.USER),
            "login",    new RateLimitPolicy("login", 5, 0.05, 1, IdentityStrategy.IP)
        );
    }

    @Bean
    public List<RouteRateLimitRule> routeRateLimitRules() {
        return List.of(
            new RouteRateLimitRule(HttpMethod.GET,  "/api/v1/events",                 "events"),
            new RouteRateLimitRule(HttpMethod.GET,  "/api/v1/shows/*/seatmap",         "seatmap"),
            new RouteRateLimitRule(HttpMethod.POST, "/api/v1/shows/*/holds",           "holds"),
            new RouteRateLimitRule(HttpMethod.POST, "/api/v1/bookings",                "bookings"),
            new RouteRateLimitRule(HttpMethod.POST, "/api/v1/auth/login",              "login")
        );
    }
}
