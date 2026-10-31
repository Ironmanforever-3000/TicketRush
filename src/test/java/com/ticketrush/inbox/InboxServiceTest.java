package com.ticketrush.inbox;

import com.ticketrush.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboxServiceTest extends AbstractIntegrationTest {

    @Autowired
    private InboxService inboxService;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM processed_events");
    }

    @Test
    void runOnceExecutesWorkAndInsertsRecord() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        inboxService.runOnce("group1", "evt-1", counter::incrementAndGet);

        assertThat(counter.get()).isEqualTo(1);
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer_group = 'group1' AND event_id = 'evt-1'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void runOnceSkipsDuplicatesAndIncrementsCounter() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        long before = InboxService.skippedDuplicates.get();

        inboxService.runOnce("group1", "evt-2", counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);

        inboxService.runOnce("group1", "evt-2", counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);
        assertThat(InboxService.skippedDuplicates.get()).isEqualTo(before + 1);
    }

    @Test
    void runOnceRollsBackOnException() {
        assertThatThrownBy(() -> inboxService.runOnce("group1", "evt-fail", () -> {
            throw new RuntimeException("simulated work error");
        })).isInstanceOf(RuntimeException.class);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer_group = 'group1' AND event_id = 'evt-fail'", Integer.class);
        assertThat(count).isZero();
    }
}
