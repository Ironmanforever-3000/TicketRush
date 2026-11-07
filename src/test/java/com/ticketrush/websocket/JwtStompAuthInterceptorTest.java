package com.ticketrush.websocket;

import com.ticketrush.ratelimit.RateLimitPolicy;
import com.ticketrush.ratelimit.RateLimitResult;
import com.ticketrush.ratelimit.RateLimitService;
import com.ticketrush.security.SecurityBeansConfig;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtStompAuthInterceptorTest {
    private static final String SECRET_BASE64 = "dGVzdC1vbmx5LXNlY3JldC10ZXN0LW9ubHktc2VjcmV0LXRlc3Qtb25seS1zZWNyZXQ=";
    private static final String ISSUER = "ticketrush-api";

    private final SecurityBeansConfig config = new SecurityBeansConfig();
    private final SecretKey key = config.jwtSecretKey(SECRET_BASE64);
    private final JwtDecoder decoder = config.jwtDecoder(key, ISSUER);
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();
    private final JwtStompAuthInterceptor interceptor = new JwtStompAuthInterceptor(decoder, converter);

    private String token(String role, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        Instant issuedAt = expiresAt.minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject("test@example.com")
                .claim("role", role)
                .claim("userId", 123L)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void validConnectSetsUser() {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer " + token("CUSTOMER", Duration.ofMinutes(15)));
        var result = interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), mock(MessageChannel.class));
        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNotNull();
    }

    @Test
    void connectWithNoAuthorizationHeaderIsRejected() {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        var msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(msg, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithExpiredTokenIsRejected() {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer " + token("CUSTOMER", Duration.ofMinutes(-5)));
        var msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(msg, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeWithNoUserIsRejected() {
        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/shows/101/seats");
        var msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(msg, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void authenticatedSubscribeToAllowedPatternSucceeds() {
        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/shows/101/seats");
        accessor.setUser(new TestingAuthenticationToken("123", null, "ROLE_CUSTOMER"));
        var msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatCode(() -> interceptor.preSend(msg, mock(MessageChannel.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void authenticatedSubscribeToDisallowedDestinationIsRejected() {
        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/admin/whatever");
        accessor.setUser(new TestingAuthenticationToken("123", null, "ROLE_CUSTOMER"));
        var msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(msg, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectExceedingRateLimitIsRejected() {
        RateLimitService mockRls = mock(RateLimitService.class);
        when(mockRls.consume(anyString(), any(RateLimitPolicy.class)))
                .thenReturn(new RateLimitResult(false, 0, 5, System.currentTimeMillis() / 1000 + 5));

        JwtStompAuthInterceptor limitedInterceptor = new JwtStompAuthInterceptor(decoder, converter, mockRls);

        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer " + token("CUSTOMER", Duration.ofMinutes(15)));
        var msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> limitedInterceptor.preSend(msg, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Rate limit exceeded for WebSocket connections");
    }

    @Test
    void subscribeExceedingRateLimitIsRejected() {
        RateLimitService mockRls = mock(RateLimitService.class);
        when(mockRls.consume(anyString(), any(RateLimitPolicy.class)))
                .thenReturn(new RateLimitResult(false, 0, 5, System.currentTimeMillis() / 1000 + 5));

        JwtStompAuthInterceptor limitedInterceptor = new JwtStompAuthInterceptor(decoder, converter, mockRls);

        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/shows/101/seats");
        accessor.setUser(new TestingAuthenticationToken("123", null, "ROLE_CUSTOMER"));
        var msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> limitedInterceptor.preSend(msg, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Rate limit exceeded for WebSocket subscriptions");
    }
}
