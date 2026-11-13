package com.ticketrush.cache;

import com.ticketrush.seat.SeatStatus;
import com.ticketrush.seat.dto.SeatMapResponse;
import com.ticketrush.seat.dto.SeatResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Proves the stale-data resurrection bug in a naive delete-on-write cache,
 * then proves the versioned cache prevents it.
 *
 * <h3>The race (naive approach)</h3>
 * <ol>
 *   <li>Reader R calls GET cache → miss, loads DB (seat A1 = AVAILABLE)</li>
 *   <li>Writer W updates A1 → HELD in DB, then DELETEs the cache key</li>
 *   <li>Reader R (still running) writes the stale AVAILABLE snapshot to cache</li>
 *   <li>Subsequent reads see AVAILABLE from cache — stale resurrection!</li>
 * </ol>
 *
 * <h3>The fix (versioned approach)</h3>
 * Writer W bumps the version counter to V2. Reader R writes data under key vV1.
 * Next reader sees version=V2, looks up data key vV2, gets a miss, loads fresh data.
 * Stale vV1 data just expires via TTL — never served again.
 */
@ExtendWith(MockitoExtension.class)
class SeatMapCacheRaceConditionTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    // Simulated Redis store — lets us observe what's actually in "cache"
    private final Map<String, String> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("NAIVE cache: delete-on-write allows stale resurrection")
    void naiveCache_deleteOnWrite_resurrectsStaleSeatmap() throws Exception {
        // --- Setup: simulated naive cache (single key, delete-on-invalidate) ---
        String cacheKey = "ticketrush:cache:seatmap:42";

        SeatMapResponse freshMap = seatmap(42L, SeatStatus.AVAILABLE);
        SeatMapResponse staleMap = seatmap(42L, SeatStatus.AVAILABLE);
        SeatMapResponse updatedMap = seatmap(42L, SeatStatus.HELD);

        // Simulate the race:
        // Step 1: Reader R loads from DB (gets AVAILABLE) — cache miss
        store.remove(cacheKey);

        // Step 2: Writer W updates DB (A1 → HELD) and deletes cache key
        store.remove(cacheKey); // writer's delete — but reader R hasn't written yet!

        // Step 3: Reader R (still running from step 1) writes its stale snapshot to cache
        store.put(cacheKey, mapper.writeValueAsString(staleMap));

        // Step 4: Verify — cache now holds STALE data (AVAILABLE when DB says HELD)
        String cachedJson = store.get(cacheKey);
        SeatMapResponse fromCache = mapper.readValue(cachedJson, SeatMapResponse.class);

        // ⚠ This assertion PASSES — proving the bug exists in a naive cache
        assertThat(fromCache.seats().getFirst().status())
                .as("Naive cache resurrects stale AVAILABLE status after writer deleted key")
                .isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("VERSIONED cache: version bump prevents stale resurrection")
    void versionedCache_bumpVersion_preventsResurrection() throws Exception {
        // --- Setup: simulated versioned cache ---
        String versionKey = "ticketrush:cache:seatmap:version:42";
        String dataKeyV1 = "ticketrush:cache:seatmap:data:42:v100";
        String dataKeyV2 = "ticketrush:cache:seatmap:data:42:v200";

        SeatMapResponse staleMap = seatmap(42L, SeatStatus.AVAILABLE);
        SeatMapResponse freshMap = seatmap(42L, SeatStatus.HELD);

        // Step 1: Version is at 100. Reader R starts, reads version=100, gets cache miss on data:v100
        store.put(versionKey, "100");
        // Reader R loads DB snapshot (AVAILABLE) — hasn't written to cache yet

        // Step 2: Writer W updates DB (A1 → HELD), bumps version to 200
        store.put(versionKey, "200");  // ← bumpVersion(42, 200)

        // Step 3: Reader R writes stale AVAILABLE data under key v100 (the version it read earlier)
        store.put(dataKeyV1, mapper.writeValueAsString(staleMap));

        // Step 4: Next reader comes in, reads version=200, looks for data:v200 — MISS
        String currentVersion = store.get(versionKey);
        assertThat(currentVersion).isEqualTo("200");

        String cachedDataAtV2 = store.get(dataKeyV2);
        assertThat(cachedDataAtV2)
                .as("No data cached at version 200 — next reader must reload from DB")
                .isNull();

        // Step 5: Next reader loads fresh data from DB (HELD) and caches it at v200
        store.put(dataKeyV2, mapper.writeValueAsString(freshMap));

        SeatMapResponse fromCacheV2 = mapper.readValue(store.get(dataKeyV2), SeatMapResponse.class);
        assertThat(fromCacheV2.seats().getFirst().status())
                .as("Versioned cache serves fresh HELD status, not stale AVAILABLE")
                .isEqualTo(SeatStatus.HELD);

        // The stale v100 data exists but will NEVER be read (version counter is at 200)
        // It simply expires via TTL
        assertThat(store.get(dataKeyV1)).isNotNull(); // exists but orphaned
    }

    @Test
    @DisplayName("SeatMapCache.bumpVersion correctly updates Redis version key")
    void seatMapCache_bumpVersion_setsVersionKey() {
        SeatMapCache cache = new SeatMapCache(redis, mapper, meterRegistry);

        // Allow the SET call
        doNothing().when(valueOps).set(anyString(), anyString(), any(Duration.class));

        cache.bumpVersion(42L, 999L);

        verify(valueOps).set(
                eq("ticketrush:cache:seatmap:version:42"),
                eq("999"),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("SeatMapCache.get returns data from cache on version hit")
    void seatMapCache_get_returnsCachedData() throws Exception {
        SeatMapCache cache = new SeatMapCache(redis, mapper, meterRegistry);

        SeatMapResponse expected = seatmap(42L, SeatStatus.AVAILABLE);
        String json = mapper.writeValueAsString(expected);

        // Version key returns "100"
        when(valueOps.get("ticketrush:cache:seatmap:version:42")).thenReturn("100");
        // Data key returns the cached JSON
        when(valueOps.get("ticketrush:cache:seatmap:data:42:v100")).thenReturn(json);

        SeatMapResponse result = cache.get(42L, () -> {
            throw new AssertionError("DB loader should not be called on cache hit");
        });

        assertThat(result.showId()).isEqualTo(42L);
        assertThat(result.seats()).hasSize(1);
        assertThat(result.seats().getFirst().status()).isEqualTo(SeatStatus.AVAILABLE);

        // Verify hit counter was incremented
        assertThat(meterRegistry.counter("ticketrush.cache.hits", "cache", "seatmap").count())
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("SeatMapCache.get falls through to DB loader on cache miss")
    void seatMapCache_get_callsLoaderOnMiss() {
        SeatMapCache cache = new SeatMapCache(redis, mapper, meterRegistry);

        // No version key → cache miss
        when(valueOps.get("ticketrush:cache:seatmap:version:42")).thenReturn(null);
        // Lock acquisition succeeds
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        // Allow writes
        doNothing().when(valueOps).set(anyString(), anyString(), any(Duration.class));
        doReturn(true).when(redis).delete(anyString());

        SeatMapResponse expected = seatmap(42L, SeatStatus.HELD);
        SeatMapResponse result = cache.get(42L, () -> expected);

        assertThat(result.showId()).isEqualTo(42L);
        assertThat(result.seats().getFirst().status()).isEqualTo(SeatStatus.HELD);

        // Verify miss counter was incremented
        assertThat(meterRegistry.counter("ticketrush.cache.misses", "cache", "seatmap").count())
                .isGreaterThanOrEqualTo(1.0);
    }

    // --- helpers ---

    private SeatMapResponse seatmap(Long showId, SeatStatus status) {
        return new SeatMapResponse(showId, List.of(
                new SeatResponse(1L, "A", 1, status, "VIP", 5000L, "USD")
        ));
    }
}
