package com.ticketrush.concurrency;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlockPreventionTest {

    @Test
    void shouldNormalizeSeatIdsToPreventDeadlock() {
        // Given an input array where ids are duplicated and out of order
        List<Long> requestedSeatIds = List.of(9L, 5L, 9L);

        // When the IDs are normalized before lock acquisition
        List<Long> normalizedIds = requestedSeatIds.stream()
                .distinct()
                .sorted()
                .toList();

        // Then we should get a canonical ascending list
        assertThat(normalizedIds)
                .containsExactly(5L, 9L);
    }
}
