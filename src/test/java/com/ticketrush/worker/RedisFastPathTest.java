package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RedisFastPathTest extends AbstractWorkerIntegrationTest {
    @Autowired StreamConsumerRunner runner;

    static final AtomicInteger calls = new AtomicInteger();

    @TestConfiguration
    static class CountingHandlerConfig {
        @Bean
        StreamEventHandler counting() {
            return new StreamEventHandler() {
                public String group() { return "ticket-generation"; }
                public Set<String> eventTypes() { return Set.of("TEST_EVENT"); }
                public void handle(StreamEvent e) { calls.incrementAndGet(); }
            };
        }
    }

    @Test
    void secondDeliveryOfAnAlreadyDoneEventSkipsTheHandlerEntirely() {
        calls.set(0);
        String eventId = "555555";
        publishRaw(eventId, "TEST_EVENT", "{}");
        runner.start();
        await().untilAsserted(() ->
                assertThat(redis.opsForValue().get("idempotency:ticket-generation:" + eventId)).isEqualTo("DONE"));

        publishRaw(eventId, "TEST_EVENT", "{}");           // same eventId, redelivered
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(redis.opsForStream().pending(stream, "ticket-generation").getTotalPendingMessages()).isZero());
        // the counting bean's call count stays at 1 - assert via the bean if you expose it, or
        // via a spy; the key proof here is that both deliveries end up acked with no error.
        assertThat(calls.get()).isEqualTo(1);
        runner.stop();
    }
}
