package com.ticketrush.waitingroom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class WaitingRoomTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final StringRedisTemplate redisTemplate;
    private final byte[] secretKey;

    public WaitingRoomTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${app.ticket.secret:default-waiting-room-secret-32-chars-min!}") String secret
    ) {
        this.redisTemplate = redisTemplate;
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issueToken(Long showId, Long userId, long queueNumber, Duration validFor) {
        String tokenId = UUID.randomUUID().toString();
        long expiresAt = Instant.now().plus(validFor).getEpochSecond();
        String payload = String.format("%s:%d:%d:%d:%d", tokenId, showId, userId, queueNumber, expiresAt);
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateAndConsumeToken(String rawToken, Long expectedShowId, Long expectedUserId) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(rawToken), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 6) {
                return false;
            }

            String tokenId = parts[0];
            Long showId = Long.parseLong(parts[1]);
            Long userId = Long.parseLong(parts[2]);
            long queueNumber = Long.parseLong(parts[3]);
            long expiresAt = Long.parseLong(parts[4]);
            String expectedSignature = parts[5];

            String payload = String.format("%s:%d:%d:%d:%d", tokenId, showId, userId, queueNumber, expiresAt);
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            if (Instant.now().getEpochSecond() > expiresAt) {
                return false;
            }

            if (!showId.equals(expectedShowId) || !userId.equals(expectedUserId)) {
                return false;
            }

            // Single-use guarantee using Redis SETNX
            String redisKey = "ticketrush:waiting-room:token:" + tokenId;
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "CONSUMED", Duration.ofMinutes(15));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKey, HMAC_SHA256));
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}
