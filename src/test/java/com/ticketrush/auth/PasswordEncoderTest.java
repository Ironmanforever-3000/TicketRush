package com.ticketrush.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    void passwordShouldBeEncodedAndVerified() {
        String rawPassword = "StrongPassword123!";
        String hash = encoder.encode(rawPassword);

        assertThat(hash).isNotEqualTo(rawPassword);
        assertThat(encoder.matches(rawPassword, hash)).isTrue();
        assertThat(encoder.matches("WrongPassword123!", hash)).isFalse();
    }

    @Test
    void samePasswordShouldProduceDifferentHashes() {
        String password = "StrongPassword123!";
        String hash1 = encoder.encode(password);
        String hash2 = encoder.encode(password);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(encoder.matches(password, hash1)).isTrue();
        assertThat(encoder.matches(password, hash2)).isTrue();
    }
}
