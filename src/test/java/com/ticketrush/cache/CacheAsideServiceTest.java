package com.ticketrush.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheAsideServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private CacheAsideService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        cacheService = new CacheAsideService(redis, mapper, meterRegistry);
    }

    @Test
    @DisplayName("getOrLoad returns cached value and increments hit counter")
    void getOrLoad_hit() throws Exception {
        String key = "ticketrush:cache:events:Music:0:10";
        List<String> cachedData = List.of("Event 1", "Event 2");
        when(valueOps.get(key)).thenReturn(mapper.writeValueAsString(cachedData));

        List<String> result = cacheService.getOrLoad("events", key, Duration.ofSeconds(45),
                new TypeReference<List<String>>() {},
                () -> { throw new AssertionError("Loader must not be called on cache hit"); });

        assertThat(result).containsExactly("Event 1", "Event 2");
        assertThat(meterRegistry.counter("ticketrush.cache.hits", "cache", "events").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("ticketrush.cache.misses", "cache", "events").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getOrLoad invokes loader on miss, caches result with TTL, and increments miss counter")
    void getOrLoad_miss() {
        String key = "ticketrush:cache:events:Sports:0:10";
        when(valueOps.get(key)).thenReturn(null);
        doNothing().when(valueOps).set(eq(key), anyString(), any(Duration.class));

        AtomicInteger loaderCalls = new AtomicInteger(0);
        List<String> result = cacheService.getOrLoad("events", key, Duration.ofSeconds(45),
                new TypeReference<List<String>>() {},
                () -> {
                    loaderCalls.incrementAndGet();
                    return List.of("Game 1");
                });

        assertThat(result).containsExactly("Game 1");
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(valueOps).set(eq(key), anyString(), any(Duration.class));
        assertThat(meterRegistry.counter("ticketrush.cache.misses", "cache", "events").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("ticketrush.cache.hits", "cache", "events").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getOrLoad falls through cleanly when Redis read fails")
    void getOrLoad_redisFailure_swallowed() {
        String key = "ticketrush:cache:events:fails:0:10";
        when(valueOps.get(key)).thenThrow(new RuntimeException("Redis connection refused"));

        List<String> result = cacheService.getOrLoad("events", key, Duration.ofSeconds(45),
                new TypeReference<List<String>>() {},
                () -> List.of("Fallback"));

        assertThat(result).containsExactly("Fallback");
    }

    @Test
    @DisplayName("evict deletes key from Redis")
    void evict_deletesKey() {
        String key = "ticketrush:cache:events:Music:0:10";
        doReturn(true).when(redis).delete(key);

        cacheService.evict(key);

        verify(redis).delete(key);
    }
}
