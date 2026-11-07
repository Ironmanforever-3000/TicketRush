package com.ticketrush.websocket;

import com.ticketrush.ratelimit.IdentityStrategy;
import com.ticketrush.ratelimit.RateLimitPolicy;
import com.ticketrush.ratelimit.RateLimitResult;
import com.ticketrush.ratelimit.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class JwtStompAuthInterceptor implements ChannelInterceptor {
    private static final Pattern SHOW_SEATS_TOPIC = Pattern.compile("^/topic/shows/\\d+/seats$");

    private static final RateLimitPolicy WS_CONNECT_POLICY =
            new RateLimitPolicy("ws-connect", 10, 0.2, 1, IdentityStrategy.IP);
    private static final RateLimitPolicy WS_SUBSCRIBE_POLICY =
            new RateLimitPolicy("ws-subscribe", 20, 1.0, 1, IdentityStrategy.USER);

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final RateLimitService rateLimitService;

    @Autowired
    public JwtStompAuthInterceptor(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Autowired(required = false) RateLimitService rateLimitService
    ) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.rateLimitService = rateLimitService;
    }

    public JwtStompAuthInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter) {
        this(jwtDecoder, jwtAuthenticationConverter, null);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            if (rateLimitService != null) {
                String clientIp = extractClientIp(accessor);
                String key = "ticketrush:ratelimit:ws-connect:ip:" + clientIp;
                RateLimitResult result = rateLimitService.consume(key, WS_CONNECT_POLICY);
                if (!result.allowed()) {
                    throw new AccessDeniedException("Rate limit exceeded for WebSocket connections");
                }
            }

            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing JWT token");
            }
            try {
                Jwt jwt = jwtDecoder.decode(auth.substring(7));
                accessor.setUser(jwtAuthenticationConverter.convert(jwt));   // associates this Principal with the session
            } catch (JwtException ex) {
                throw new AccessDeniedException("Invalid or expired token", ex);
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (accessor.getUser() == null) {
                throw new AccessDeniedException("Not authenticated");
            }

            if (rateLimitService != null) {
                String userId = accessor.getUser().getName();
                String key = "ticketrush:ratelimit:ws-subscribe:user:" + userId;
                RateLimitResult result = rateLimitService.consume(key, WS_SUBSCRIBE_POLICY);
                if (!result.allowed()) {
                    throw new AccessDeniedException("Rate limit exceeded for WebSocket subscriptions");
                }
            }

            String destination = accessor.getDestination();
            if (destination == null || !SHOW_SEATS_TOPIC.matcher(destination).matches()) {
                throw new AccessDeniedException("Unsupported subscription destination: " + destination);
            }
        }
        return message;
    }

    private String extractClientIp(StompHeaderAccessor accessor) {
        String forwarded = accessor.getFirstNativeHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        if (accessor.getSessionAttributes() != null && accessor.getSessionAttributes().containsKey("clientIp")) {
            return String.valueOf(accessor.getSessionAttributes().get("clientIp"));
        }
        if (accessor.getSessionId() != null) {
            return accessor.getSessionId();
        }
        return "unknown";
    }
}
