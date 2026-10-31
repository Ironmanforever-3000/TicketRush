package com.ticketrush.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisIdempotencyHint {
    private final StringRedisTemplate redis;
    @Value("${app.worker.idempotency-ttl-days:7}")
    private int ttlDays;

    public RedisIdempotencyHint(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Best-effort pre-check. false means "maybe not done" — Redis could be down or the key
     * evicted — the caller MUST still run the real Postgres path. true means "definitely
     * done" and the caller can skip the handler entirely.
     */
    public boolean probablyDone(String consumerName, long eventId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(consumerName, eventId)));
        } catch (RuntimeException ex) {
            return false;      // Redis unavailable: fall through, never block
        }
    }

    /**
     * Call ONLY after the Postgres transaction that did the real work has committed.
     */
    public void markDone(String consumerName, long eventId) {
        try {
            redis.opsForValue().set(key(consumerName, eventId), "DONE", Duration.ofDays(ttlDays));
        } catch (RuntimeException ex) {
            // non-fatal: Postgres marker is already the truth
        }
    }

    private String key(String consumerName, long eventId) {
        return "idempotency:" + consumerName + ":" + eventId;
    }
}
