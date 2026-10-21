package com.ticketrush.user.dto;

import com.ticketrush.auth.Role;
import java.time.OffsetDateTime;

public record UserProfileResponse(
    Long id,
    String name,
    String email,
    Role role,
    OffsetDateTime createdAt
) {}
