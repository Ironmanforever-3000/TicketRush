package com.ticketrush.websocket;

import com.ticketrush.outbox.OutboxBatchService;
import com.ticketrush.worker.AbstractWorkerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SeatStateOutboxToPubSubTest extends AbstractWorkerIntegrationTest {

    @Autowired OutboxBatchService batchService;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @Test
    void seatStateChangedGoesToPubSubNotTheStream() throws Exception {
        var blocking = new LinkedBlockingQueue<String>();
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redis.getConnectionFactory());
        container.addMessageListener((msg, pattern) -> blocking.add(new String(msg.getBody())),
                new ChannelTopic("ticketrush:seat-updates"));
        container.afterPropertiesSet();
        container.start();
        try {
            HoldFixture f = holdSeats(2);
            assertThat(batchService.publishBatch()).isGreaterThanOrEqualTo(1);

            String received = blocking.poll(5, TimeUnit.SECONDS);
            assertThat(received).isNotNull().contains("HELD").contains(f.showId().toString());

            var streamEntries = redis.opsForStream().range("ticketrush:events", Range.unbounded());
            assertThat(streamEntries).noneMatch(r -> "SEAT_STATE_CHANGED".equals(r.getValue().get("eventType")));
        } finally {
            container.stop();
        }
    }

    @Test
    void subsequentEventsHaveIncreasingVersion() throws Exception {
        holdSeats(1);
        Long v1 = jdbc.queryForObject(
                "SELECT id FROM outbox WHERE event_type='SEAT_STATE_CHANGED' ORDER BY id DESC LIMIT 1", Long.class);
        holdSeats(1);
        Long v2 = jdbc.queryForObject(
                "SELECT id FROM outbox WHERE event_type='SEAT_STATE_CHANGED' ORDER BY id DESC LIMIT 1", Long.class);
        assertThat(v2).isGreaterThan(v1);
    }
}
