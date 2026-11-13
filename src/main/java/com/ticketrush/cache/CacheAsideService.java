package com.ticketrush.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Generic Redis cache-aside helper with TTL jitter and Micrometer hit/miss counters.
 *
 * <p>Cache keys use the {@code ticketrush:cache:} prefix convention established
 * in earlier phases. Every cache failure (read or write) is swallowed —
 * a broken Redis must never break a user request.</p>
 */
@Component
public class CacheAsideService {

    private static final Logger log = LoggerFactory.getLogger(CacheAsideService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MeterRegistry registry;

    public CacheAsideService(StringRedisTemplate redis, ObjectMapper mapper, MeterRegistry registry) {
        this.redis = redis;
        this.mapper = mapper;
        this.registry = registry;
    }

    /**
     * Look up {@code key} in Redis; on miss, call {@code loader}, cache the result with
     * a jittered TTL, and return it.
     *
     * @param cacheName  logical name used as the Micrometer tag value (e.g. "events", "seatmap")
     * @param key        full Redis key including prefix
     * @param baseTtl    base time-to-live before jitter is added
     * @param type       Jackson type reference for deserialization
     * @param loader     data-source callback invoked on cache miss
     * @return the cached or freshly loaded value
     */
    public <T> T getOrLoad(String cacheName, String key, Duration baseTtl,
                           TypeReference<T> type, Supplier<T> loader) {
        // --- try cache ---
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                registry.counter("ticketrush.cache.hits", "cache", cacheName).increment();
                return mapper.readValue(cached, type);
            }
        } catch (Exception e) {
            log.warn("Cache read failed for key={}, falling through to loader", key, e);
        }

        // --- miss: load from source ---
        registry.counter("ticketrush.cache.misses", "cache", cacheName).increment();
        T value = loader.get();

        // --- write back ---
        try {
            String json = mapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, jittered(baseTtl));
        } catch (Exception e) {
            log.warn("Cache write failed for key={}", key, e);
        }

        return value;
    }

    /** Remove a single key. Failures are swallowed. */
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Cache evict failed for key={}", key, e);
        }
    }

    /**
     * Add up to 20 % random jitter so that thousands of keys written in the same
     * burst don't all expire in the same second (thundering-herd prevention).
     */
    private Duration jittered(Duration base) {
        long jitterBound = base.toMillis() / 5 + 1;   // +1 avoids zero bound
        return base.plusMillis(ThreadLocalRandom.current().nextLong(0, jitterBound));
    }
}
