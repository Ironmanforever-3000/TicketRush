package com.ticketrush.outbox;

import com.ticketrush.realtime.RedisSeatPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxBatchService {
    private static final Logger log = LoggerFactory.getLogger(OutboxBatchService.class);

    private final OutboxClaimService claims;
    private final StringRedisTemplate redis;
    private final RedisSeatPublisher seatPublisher;
    @Value("${app.events.stream}") private String stream;
    @Value("${app.outbox.batch-size:100}") private int batchSize;
    @Value("${app.outbox.lease-seconds:30}") private int leaseSeconds;

    public OutboxBatchService(OutboxClaimService claims, StringRedisTemplate redis) {
        this(claims, redis, new RedisSeatPublisher(redis));
    }

    @Autowired
    public OutboxBatchService(OutboxClaimService claims, StringRedisTemplate redis, RedisSeatPublisher seatPublisher) {
        this.claims = claims;
        this.redis = redis;
        this.seatPublisher = seatPublisher;
    }

    /** Claims a batch (one short tx), then publishes each row to Redis with NO transaction
     *  held open. Each successful publish is marked in its own short tx, right after XADD
     *  returns, so a crash between the two never loses the commit without losing the publish. */
    public int publishBatch() {
        String batchToken = UUID.randomUUID().toString();
        int claimed = claims.claim(batchToken, leaseSeconds, batchSize);
        if (claimed == 0) return 0;

        List<OutboxEvent> events = claims.findClaimed(batchToken);
        int published = 0;
        for (OutboxEvent e : events) {
            if (!publishOne(e)) break;    // stop the batch on first Redis failure; rest re-lease later
            claims.markPublished(e.getId());
            published++;
        }
        return published;
    }

    private boolean publishOne(OutboxEvent e) {
        try {
            if ("SEAT_STATE_CHANGED".equals(e.getEventType())) {
                seatPublisher.publish(e.getPayload());          // ephemeral Pub/Sub — no Stream, no consumer groups
            } else {
                Map<String, String> fields = new HashMap<>();
                fields.put("eventId", e.getId().toString());          // stable across any republish
                fields.put("eventType", e.getEventType());
                fields.put("payload", e.getPayload());                // already-serialized JSON from booking confirmation
                fields.put("occurredAt", e.getCreatedAt().toString());
                redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(fields));
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable; outbox event {} stays claimed for re-lease: {}", e.getId(), ex.toString());
            return false;
        }
    }
}
