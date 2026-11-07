package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterTest extends AbstractWorkerIntegrationTest {
    @Autowired StreamMessageProcessor processor;
    @Autowired PendingMessageReclaimer reclaimer;
    @Autowired ConsumerGroupInitializer init;

    @TestConfiguration
    static class AlwaysFailingHandlerConfig {
        @Bean
        StreamEventHandler alwaysFails() {
            return new StreamEventHandler() {
                public String group() { return "ticket-generation"; }
                public Set<String> eventTypes() { return Set.of("POISON"); }
                public void handle(StreamEvent e) { throw new RuntimeException("deliberately broken"); }
            };
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void messageExceedingMaxDeliveriesReachesDeadLetterStream(@Autowired StreamEventHandler alwaysFails) {
        init.ensureGroup("ticket-generation");
        publishRaw("777", "POISON", "{}");
        ReflectionTestUtils.setField(reclaimer, "maxDeliveries", 1);
        ReflectionTestUtils.setField(reclaimer, "pendingIdleSeconds", 0L);

        var read = redis.<String, String>opsForStream().read(Consumer.from("ticket-generation", "w1"),
                StreamReadOptions.empty().count(1), StreamOffset.create(stream, ReadOffset.lastConsumed()));
        processor.process(alwaysFails, read.get(0), false);          // first delivery: retries in-process, stays pending

        reclaimer.reclaimAbandoned();                                  // delivery count now >= 1 -> dead-letters

        assertThat(redis.opsForStream().pending(stream, "ticket-generation").getTotalPendingMessages()).isZero();
        assertThat(redis.opsForStream().size("ticketrush:dead-letter")).isGreaterThanOrEqualTo(1);
    }
}
