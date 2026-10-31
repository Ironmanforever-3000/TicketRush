package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StreamMessageProcessorTest {

    @Test
    void backoffDelaysGrowAndAreRandomizedWithinCap() {
        StreamMessageProcessor processor = new StreamMessageProcessor(null, null, null);
        ReflectionTestUtils.setField(processor, "baseDelayMs", 250L);
        ReflectionTestUtils.setField(processor, "maxDelayMs", 10000L);

        // Attempt 1: cap = 250 -> range [125, 250]
        Set<Long> attempt1Values = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            long delay = processor.backoffMs(1);
            assertThat(delay).isBetween(125L, 250L);
            attempt1Values.add(delay);
        }
        assertThat(attempt1Values.size()).isGreaterThan(1);

        // Attempt 2: cap = 500 -> range [250, 500]
        Set<Long> attempt2Values = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            long delay = processor.backoffMs(2);
            assertThat(delay).isBetween(250L, 500L);
            attempt2Values.add(delay);
        }
        assertThat(attempt2Values.size()).isGreaterThan(1);

        // Attempt 3: cap = 1000 -> range [500, 1000]
        for (int i = 0; i < 50; i++) {
            long delay = processor.backoffMs(3);
            assertThat(delay).isBetween(500L, 1000L);
        }

        // Cap growth progression
        assertThat(processor.backoffCap(1)).isEqualTo(250L);
        assertThat(processor.backoffCap(2)).isEqualTo(500L);
        assertThat(processor.backoffCap(3)).isEqualTo(1000L);
        assertThat(processor.backoffCap(10)).isEqualTo(10000L); // capped at maxDelayMs
    }
}
