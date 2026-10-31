package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StreamConsumerRunnerTest extends AbstractWorkerIntegrationTest {
    @Autowired StreamConsumerRunner runner;
    @Autowired ConsumerGroupInitializer init;

    static final java.util.concurrent.atomic.AtomicBoolean handled = new java.util.concurrent.atomic.AtomicBoolean();

    @TestConfiguration
    static class NoOpHandlerConfig {
        @Bean
        StreamEventHandler testHandler() {
            return new StreamEventHandler() {
                public String group() {
                    return "ticket-generation";
                }

                public Set<String> eventTypes() {
                    return Set.of("TEST_EVENT");
                }

                public void handle(StreamEvent e) {
                    handled.set(true);
                }
            };
        }
    }

    @Test
    void messageIsAckedAfterSuccessfulHandling() {
        handled.set(false);
        init.ensureGroup("ticket-generation");
        publishRaw("123456", "TEST_EVENT", "{}");
        runner.start();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(handled.get()).isTrue();
            assertThat(redis.opsForStream().pending(stream, "ticket-generation").getTotalPendingMessages()).isZero();
        });
        runner.stop();
    }

    @Test
    void malformedEventIdIsAckedWithoutLeavingItPending() {
        init.ensureGroup("ticket-generation");
        publishRaw("malformed-not-a-number", "TEST_EVENT", "{}");
        runner.start();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(redis.opsForStream().pending(stream, "ticket-generation").getTotalPendingMessages()).isZero());
        runner.stop();
    }
}
