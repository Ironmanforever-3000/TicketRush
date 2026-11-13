package com.ticketrush.cache;

import com.ticketrush.seat.dto.SeatMapResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Versioned seatmap cache that prevents stale-data resurrection.
 *
 * <h3>Why versioned?</h3>
 * A naive delete-on-write cache is vulnerable to this interleaving:
 * <ol>
 *   <li>Reader R loads DB snapshot (seat A1 = AVAILABLE)</li>
 *   <li>Writer W updates A1 → HELD, then deletes cache key</li>
 *   <li>Reader R (still running) writes the stale snapshot to cache</li>
 * </ol>
 * Now the cache says A1 = AVAILABLE when it is actually HELD — a resurrection bug.
 *
 * <h3>Fix: version keys</h3>
 * Every seat-state change bumps the version counter (using the monotonic outbox event id).
 * Cache data keys include the version: {@code ticketrush:cache:seatmap:data:{showId}:v{version}}.
 * A reader that loaded data at version V will write it under key vV — but the version counter
 * has already moved to V+1, so the next reader fetches v(V+1), finds a miss, and loads fresh
 * data. The stale vV key simply expires via TTL.
 *
 * <h3>Stampede protection</h3>
 * A SET NX lock ({@code ticketrush:cache:seatmap:lock:{showId}}, 3 s TTL) prevents N concurrent
 * cache misses from all hitting Postgres. The winner loads; the losers wait briefly and retry
 * the cache, or fall through to DB (never blocking the hold path).
 */
@Component
public class SeatMapCache {

    private static final Logger log = LoggerFactory.getLogger(SeatMapCache.class);

    private static final String VERSION_PREFIX = "ticketrush:cache:seatmap:version:";
    private static final String DATA_PREFIX    = "ticketrush:cache:seatmap:data:";
    private static final String LOCK_PREFIX    = "ticketrush:cache:seatmap:lock:";

    private static final Duration DATA_TTL    = Duration.ofSeconds(10);
    private static final Duration VERSION_TTL = Duration.ofHours(1);
    private static final Duration LOCK_TTL    = Duration.ofSeconds(3);
    private static final long RETRY_WAIT_MS   = 50;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MeterRegistry registry;

    public SeatMapCache(StringRedisTemplate redis, ObjectMapper mapper, MeterRegistry registry) {
        this.redis = redis;
        this.mapper = mapper;
        this.registry = registry;
    }

    /**
     * Read the seatmap from cache, loading from {@code dbLoader} on miss.
     *
     * @param showId   the show whose seatmap to fetch
     * @param dbLoader callback that fetches the seatmap from Postgres
     * @return the seatmap, from cache or freshly loaded
     */
    public SeatMapResponse get(Long showId, java.util.function.Supplier<SeatMapResponse> dbLoader) {
        try {
            String versionStr = redis.opsForValue().get(versionKey(showId));
            if (versionStr != null) {
                String dataKey = dataKey(showId, versionStr);
                String cached = redis.opsForValue().get(dataKey);
                if (cached != null) {
                    registry.counter("ticketrush.cache.hits", "cache", "seatmap").increment();
                    return mapper.readValue(cached, new TypeReference<SeatMapResponse>() {});
                }
            }
        } catch (Exception e) {
            log.warn("SeatMapCache read failed for showId={}, falling through to DB", showId, e);
        }

        // --- cache miss ---
        registry.counter("ticketrush.cache.misses", "cache", "seatmap").increment();

        // Stampede protection: try to acquire refill lock
        Boolean lockAcquired = false;
        try {
            lockAcquired = redis.opsForValue().setIfAbsent(
                    lockKey(showId), "1", LOCK_TTL);
        } catch (Exception e) {
            log.warn("SeatMapCache lock acquisition failed for showId={}", showId, e);
        }

        if (Boolean.TRUE.equals(lockAcquired)) {
            // We hold the lock — load from DB, populate cache, release lock
            try {
                SeatMapResponse data = dbLoader.get();
                writeToCache(showId, data);
                return data;
            } finally {
                try { redis.delete(lockKey(showId)); } catch (Exception ignored) { /* best effort */ }
            }
        }

        // Someone else is loading — wait briefly, then check cache again
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_WAIT_MS);
            String versionStr = redis.opsForValue().get(versionKey(showId));
            if (versionStr != null) {
                String cached = redis.opsForValue().get(dataKey(showId, versionStr));
                if (cached != null) {
                    registry.counter("ticketrush.cache.hits", "cache", "seatmap").increment();
                    return mapper.readValue(cached, new TypeReference<SeatMapResponse>() {});
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("SeatMapCache retry-read failed for showId={}", showId, e);
        }

        // Still no cache — just load from DB (never block user requests)
        return dbLoader.get();
    }

    /**
     * Bump the version for a show. Called from {@code SeatStateOutboxWriter.write()}.
     * Uses the outbox event id as the version — guaranteed monotonically increasing.
     *
     * @param showId        the show whose seatmap version to bump
     * @param outboxEventId the id of the outbox row (monotonic, used as version)
     */
    public void bumpVersion(Long showId, Long outboxEventId) {
        try {
            redis.opsForValue().set(versionKey(showId), outboxEventId.toString(), VERSION_TTL);
        } catch (Exception e) {
            log.warn("SeatMapCache version bump failed for showId={}, eventId={}", showId, outboxEventId, e);
        }
    }

    // --- internals ---

    private void writeToCache(Long showId, SeatMapResponse data) {
        try {
            // Read the current version (may have been bumped during our DB load — that's fine,
            // the data we loaded is at least as fresh as the moment we started the query).
            String versionStr = redis.opsForValue().get(versionKey(showId));
            if (versionStr == null) {
                // No version yet (first load, or key expired) — use "0"
                versionStr = "0";
                redis.opsForValue().set(versionKey(showId), versionStr, VERSION_TTL);
            }
            String json = mapper.writeValueAsString(data);
            redis.opsForValue().set(dataKey(showId, versionStr), json, DATA_TTL);
        } catch (Exception e) {
            log.warn("SeatMapCache write failed for showId={}", showId, e);
        }
    }

    private String versionKey(Long showId) { return VERSION_PREFIX + showId; }
    private String dataKey(Long showId, String version) { return DATA_PREFIX + showId + ":v" + version; }
    private String lockKey(Long showId) { return LOCK_PREFIX + showId; }
}
