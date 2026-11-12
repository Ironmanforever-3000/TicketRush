package com.ticketrush.admin.dto;

public record AdminUserSuspensionResponse(
        Long userId,
        String email,
        String role,
        boolean active
) {
}
