package com.ticketrush.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationTest {

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret-base64}") String secretBase64;

    String token(String signWithBase64, String role, Duration ttl) {
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(signWithBase64);
        } catch (IllegalArgumentException e) {
            // fallback if someone passed a raw string instead of base64
            secretBytes = signWithBase64.getBytes(StandardCharsets.UTF_8);
        }
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretBytes));
        Instant now = Instant.now();
        Instant iat = ttl.isNegative() ? now.plus(ttl).minusSeconds(60) : now;
        Instant exp = iat.plus(ttl.abs());
        var claims = JwtClaimsSet.builder().subject("123").claim("role", role)
                .issuer("ticketrush-api")
                .issuedAt(iat).expiresAt(exp).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    String bearer(String role) { return "Bearer " + token(secretBase64, role, Duration.ofMinutes(15)); }

    @Test void customerCannotCreateEvent() throws Exception {
        mvc.perform(post("/api/v1/events").header("Authorization", bearer("CUSTOMER"))
                .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test void customerCannotReachAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/anything").header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"ORGANIZER", "ADMIN"})
    void organizerAndAdminPassSecurity(String role) throws Exception {
        int s = mvc.perform(post("/api/v1/events").header("Authorization", bearer(role))
                .contentType(APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
        assertThat(s).isNotIn(401, 403);
    }

    @Test void noTokenIs401() throws Exception {
        mvc.perform(post("/api/v1/bookings")).andExpect(status().isUnauthorized());
    }

    @Test void forgedAdminTokenIs401() throws Exception {
        String forged = "Bearer " + token(Base64.getEncoder().encodeToString("x".repeat(40).getBytes()), "ADMIN", Duration.ofMinutes(15));
        mvc.perform(get("/api/v1/admin/anything").header("Authorization", forged))
                .andExpect(status().isUnauthorized());
    }

    @Test void expiredTokenIs401() throws Exception {
        String expired = "Bearer " + token(secretBase64, "ADMIN", Duration.ofMinutes(-5));
        mvc.perform(get("/api/v1/admin/anything").header("Authorization", expired))
                .andExpect(status().isUnauthorized());
    }
}
