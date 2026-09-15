package com.ticketrush.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        // Include name as it's NOT NULL in existing table
        @NotBlank String name,
        
        @Email
        @NotBlank
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        String password
) {
}
