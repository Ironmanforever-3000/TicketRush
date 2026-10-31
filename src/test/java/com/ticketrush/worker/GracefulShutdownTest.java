package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class GracefulShutdownTest extends AbstractWorkerIntegrationTest {
    @Autowired StreamConsumerRunner runner;
    @Autowired ConsumerGroupInitializer init;

    @TestConfiguration
    static class SlowHandlerConfig {
        @Bean
        StreamEventHandler slow() {
            return new StreamEventHandler() {
                public String group() { return "ticket-generation"; }
                public Set<String> eventTypes() { return Set.of("SLOW"); }
                public void handle(StreamEvent e) throws InterruptedException { Thread.sleep(2000); }
            };
        }
    }

    @Test
    void stopWaitsForInFlightWorkBeforeReturning() throws Exception {
        init.ensureGroup("ticket-generation");
        String eventId = String.valueOf(System.currentTimeMillis());
        publishRaw(eventId, "SLOW", "{}");
        runner.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> runner.getInFlightCount() > 0);
        long t0 = System.currentTimeMillis();
        CountDownLatch done = new CountDownLatch(1);
        runner.stop(done::countDown);
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(System.currentTimeMillis() - t0).isGreaterThanOrEqualTo(1500);    // waited for the 2s job
        assertThat(redis.opsForStream().pending(stream, "ticket-generation").getTotalPendingMessages()).isZero();
    }
}
