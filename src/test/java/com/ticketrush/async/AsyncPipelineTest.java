package com.ticketrush.async;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.inbox.InboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "app.worker.enabled=true",
        "app.outbox.publisher-enabled=true",
        "app.worker.base-delay-ms=10",
        "app.worker.max-delay-ms=50",
        "app.worker.pending-idle-seconds=0",
        "app.worker.sweep-delay-ms=200"
})
public class AsyncPipelineTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired InboxService inbox;
    @Value("${app.events.stream}") String stream;
    @Value("${app.events.dead-letter-stream}") String dlq;

    @Test
    void confirmedBookingFlowsToAllThreeWorkers() throws Exception {
        Fixture f = bookedShow(3);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_qr q JOIN tickets t ON t.id = q.ticket_id WHERE t.booking_id = ?",
                    Long.class, f.bookingId())).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT tickets_sold FROM show_sales WHERE show_id = ?", Long.class, f.showId())).isEqualTo(3);
        });
    }

    @Test
    void redeliveringTheSameEventChangesNothing() throws Exception {
        Fixture f = bookedShow(3);
        await().until(() -> salesFor(f.showId()) == 3);
        long skippedBefore = inbox.skippedDuplicates.get();
        jdbc.update("UPDATE outbox SET published_at = NULL WHERE aggregate_id = ? AND event_type = 'BOOKING_CONFIRMED'", f.bookingId());
        await().until(() -> inbox.skippedDuplicates.get() >= skippedBefore + 3);   // one skip per consumer group
        assertThat(salesFor(f.showId())).isEqualTo(3);                              // not 6
    }

    @Test
    void poisonMessageEndsInTheDeadLetterStream() {
        Long before = redis.opsForStream().size(dlq);
        if (before == null) before = 0L;
        final Long initial = before;
        redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(Map.of(
                "event_id", "poison-" + UUID.randomUUID(),
                "event_type", "BOOKING_CONFIRMED",
                "aggregate_type", "BOOKING",
                "aggregate_id", "0",
                "payload", "{not json"
        )));
        await().untilAsserted(() -> assertThat(redis.opsForStream().size(dlq)).isGreaterThanOrEqualTo(initial + 3));   // one per group
    }

    private long salesFor(Long showId) {
        return jdbc.queryForObject("SELECT coalesce(max(tickets_sold), 0) FROM show_sales WHERE show_id = ?", Long.class, showId);
    }
}
