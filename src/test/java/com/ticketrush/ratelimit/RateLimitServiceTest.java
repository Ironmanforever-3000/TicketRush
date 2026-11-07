package com.ticketrush.ratelimit;

import com.ticketrush.worker.AbstractWorkerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest extends AbstractWorkerIntegrationTest {

    @Autowired
    RateLimitService rateLimiter;

    @Test
    void allowsUpToCapacityThenRejects() {
        var policy = new RateLimitPolicy("test-burst", 5, 1.0, 1);
        String key = "ratelimit:test-burst:" + UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.consume(key, policy).allowed()).isTrue();
        }

        var sixth = rateLimiter.consume(key, policy);
        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void exactlyCapacityRequestsSucceedUnderConcurrency() throws Exception {
        // THE proof that the Lua script's atomicity claim is real, not assumed.
        var policy = new RateLimitPolicy("test-concurrent", 10, 1.0, 1);
        String key = "ratelimit:test-concurrent:" + UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(50);
        var go = new CountDownLatch(1);
        var allowedCount = new AtomicInteger();

        var futures = IntStream.range(0, 50).mapToObj(i -> pool.submit(() -> {
            try {
                go.await();
                if (rateLimiter.consume(key, policy).allowed()) {
                    allowedCount.incrementAndGet();
                }
            } catch (InterruptedException ignored) {
            }
        })).toList();
        go.countDown();
        for (var f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(allowedCount.get()).isEqualTo(10);   // not 9, not 11 — exactly the bucket's capacity
    }

    @Test
    void tokensRefillOverTime() throws Exception {
        var policy = new RateLimitPolicy("test-refill", 2, 50.0, 1);   // fast refill so the test stays quick
        String key = "ratelimit:test-refill:" + UUID.randomUUID();
        rateLimiter.consume(key, policy);
        rateLimiter.consume(key, policy);
        assertThat(rateLimiter.consume(key, policy).allowed()).isFalse();

        Thread.sleep(100);   // ~5 tokens worth of refill at 50/sec
        assertThat(rateLimiter.consume(key, policy).allowed()).isTrue();
    }

    @Test
    void separateKeysAreIndependentBuckets() {
        var policy = new RateLimitPolicy("test-isolation", 1, 1.0, 1);
        String keyA = "ratelimit:test-isolation:" + UUID.randomUUID();
        String keyB = "ratelimit:test-isolation:" + UUID.randomUUID();
        assertThat(rateLimiter.consume(keyA, policy).allowed()).isTrue();
        assertThat(rateLimiter.consume(keyA, policy).allowed()).isFalse();
        assertThat(rateLimiter.consume(keyB, policy).allowed()).isTrue();   // untouched by A's exhaustion
    }
}
