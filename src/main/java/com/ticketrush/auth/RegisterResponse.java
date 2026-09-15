package com.ticketrush.auth;

public record RegisterResponse(
        Long userId,
        String name,
        String email,
        String role
) {
}
