package com.ticketrush.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
