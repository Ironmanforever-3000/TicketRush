package com.ticketrush.async;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.worker.StreamWorkerRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "app.worker.enabled=true",
        "app.worker.autostart=false",
        "app.worker.pending-idle-seconds=0",
        "app.worker.sweep-delay-ms=200",
        "app.outbox.publisher-enabled=true"
})
public class PendingRecoveryTest extends AbstractIntegrationTest {

    @Autowired StringRedisTemplate redis;
    @Autowired StreamWorkerRunner runner;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.events.stream}") String stream;

    @Test
    void deliveredButNeverAckedMessageIsRecoveredAndProcessedOnce() throws Exception {
        Fixture f = bookedShow(2);                         // publisher puts the event on the stream; real workers are not running yet
        runner.ensureGroups();
        var read = redis.<String, String>opsForStream().read(Consumer.from("ticket-worker", "ghost"),
                StreamReadOptions.empty().count(100), StreamOffset.create(stream, ReadOffset.lastConsumed()));
        assertThat(read).isNotEmpty();                     // the worker that "crashed": it saw the event and never acked
        runner.start();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_qr q JOIN tickets t ON t.id = q.ticket_id WHERE t.booking_id = ?",
                    Long.class, f.bookingId())).isEqualTo(2);
            assertThat(redis.opsForStream().pending(stream, "ticket-worker").getTotalPendingMessages()).isZero();
        });
    }
}
