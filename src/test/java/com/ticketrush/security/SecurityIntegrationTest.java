package com.ticketrush.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.DummyControllerConfig.class)
public class SecurityIntegrationTest {

    @TestConfiguration
    static class DummyControllerConfig {
        @RestController
        static class DummyController {
            @GetMapping("/api/v1/dummy/me")
            public String getMe(Authentication authentication) {
                return authentication.getName();
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/dummy/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerShouldBePublic() throws Exception {
        // Just verify it doesn't return 401
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "New User",
                          "email": "newuser@example.com",
                          "password": "Password123!"
                        }
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void loginShouldBePublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "nonexistent@example.com",
                          "password": "WrongPassword123!"
                        }
                        """))
                .andExpect(status().isUnauthorized()); // Application specific 401, not spring security 401
    }

    @Test
    void protectedEndpointWithValidJwtReturnsNot401() throws Exception {
        String token = jwtService.generateToken("alice@example.com", "CUSTOMER", 1L);

        mockMvc.perform(get("/api/v1/dummy/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        // Generate an expired token
        SecretKey key = new SecretKeySpec(java.util.Base64.getDecoder().decode("eW91ci0yNTYtYml0LXNlY3JldC15b3VyLTI1Ni1iaXQtc2VjcmV0"), "HmacSHA256");
        com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> immutableSecret = new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key);
        JwtEncoder encoder = new NimbusJwtEncoder(immutableSecret);

        Instant now = Instant.now().minus(1, ChronoUnit.DAYS); // Expired yesterday
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ticketrush-api")
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .subject("alice@example.com")
                .claim("role", "CUSTOMER")
                .claim("userId", 1L)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String expiredToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/dummy/me")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerTokenReturns401() throws Exception {
        SecretKey key = new SecretKeySpec(java.util.Base64.getDecoder().decode("eW91ci0yNTYtYml0LXNlY3JldC15b3VyLTI1Ni1iaXQtc2VjcmV0"), "HmacSHA256");
        com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> immutableSecret = new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key);
        JwtEncoder encoder = new NimbusJwtEncoder(immutableSecret);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("wrong-issuer")
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .subject("alice@example.com")
                .claim("role", "CUSTOMER")
                .claim("userId", 1L)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/dummy/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureTokenReturns401() throws Exception {
        // Use a different secret
        SecretKey key = new SecretKeySpec("some-other-secret-that-is-long-enough-32bytes!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> immutableSecret = new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key);
        JwtEncoder encoder = new NimbusJwtEncoder(immutableSecret);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ticketrush-api")
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .subject("alice@example.com")
                .claim("role", "CUSTOMER")
                .claim("userId", 1L)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/dummy/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void tamperedTokenReturns401() throws Exception {
        String token = jwtService.generateToken("alice@example.com", "CUSTOMER", 1L);
        String[] parts = token.split("\\.");
        // Change payload (the middle part)
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"hacker\"}".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        mockMvc.perform(get("/api/v1/dummy/me")
                .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/dummy/me")
                .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticationGetNameEqualsJwtSubject() throws Exception {
        String token = jwtService.generateToken("alice@example.com", "CUSTOMER", 1L);

        mockMvc.perform(get("/api/v1/dummy/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("alice@example.com"));
    }

}
