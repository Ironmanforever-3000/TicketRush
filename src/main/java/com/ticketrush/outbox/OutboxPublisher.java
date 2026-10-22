package com.ticketrush.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.outbox.publisher-enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final StringRedisTemplate redis;

    @Value("${app.events.stream}")
    private String stream;

    @Value("${app.events.max-length:100000}")
    private long maxLength;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    public OutboxPublisher(OutboxRepository outbox, StringRedisTemplate redis) {
        this.outbox = outbox;
        this.redis = redis;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:500}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = outbox.lockNextBatch(batchSize);
        for (OutboxEvent e : batch) {
            try {
                redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(Map.of(
                        "event_id", String.valueOf(e.getId()),
                        "event_type", e.getEventType(),
                        "aggregate_type", e.getAggregateType(),
                        "aggregate_id", String.valueOf(e.getAggregateId()),
                        "payload", e.getPayload())));
                e.setPublishedAt(OffsetDateTime.now());          // committed with the batch
            } catch (RuntimeException ex) {
                log.warn("Redis unavailable; outbox event {} stays unpublished: {}", e.getId(), ex.toString());
                break;                                           // retry on the next tick; earlier rows still commit
            }
        }
        if (!batch.isEmpty()) {
            try {
                redis.opsForStream().trim(stream, maxLength, true);   // approximate trim
            } catch (RuntimeException ex) {
                log.warn("Redis trim error: {}", ex.toString());
            }
        }
    }
}
