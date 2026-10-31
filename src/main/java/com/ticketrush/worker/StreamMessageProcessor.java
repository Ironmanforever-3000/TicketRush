package com.ticketrush.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class StreamMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(StreamMessageProcessor.class);

    private final StringRedisTemplate redis;
    private final RedisIdempotencyHint idempotencyHint;
    private final DeadLetterPublisher deadLetter;

    @Value("${app.events.stream:ticketrush:events}")
    private String stream;

    @Value("${app.worker.max-attempts:3}")
    private int maxAttempts;     // in-process retries within ONE delivery

    @Value("${app.worker.base-delay-ms:250}")
    private long baseDelayMs;

    @Value("${app.worker.max-delay-ms:10000}")
    private long maxDelayMs;

    public StreamMessageProcessor(StringRedisTemplate redis,
                                  RedisIdempotencyHint idempotencyHint,
                                  DeadLetterPublisher deadLetter) {
        this.redis = redis;
        this.idempotencyHint = idempotencyHint;
        this.deadLetter = deadLetter;
    }

    /**
     * true = the record is resolved (acked, success or dead-letter). false = still pending —
     * a live subscriber just moves on; the sweeper will try again next cycle.
     */
    public boolean process(StreamEventHandler handler, MapRecord<String, String, String> record, boolean giveUpNow) {
        Map<String, String> f = record.getValue();
        String eventType = f.get("eventType");
        if (eventType == null || !handler.eventTypes().contains(eventType)) {
            ack(handler.group(), record);
            return true;
        }

        long eventId;
        try {
            eventId = Long.parseLong(f.get("eventId"));
        } catch (NumberFormatException ex) {
            deadLetter.publish(handler.group(), record, "malformed eventId");   // not retryable, ever
            ack(handler.group(), record);
            return true;
        }

        if (idempotencyHint.probablyDone(handler.group(), eventId)) {
            ack(handler.group(), record);
            return true;
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                StreamEvent event = new StreamEvent(f.get("eventId"), eventType, f.get("payload"), Instant.parse(f.get("occurredAt")));
                handler.handle(event);
                idempotencyHint.markDone(handler.group(), eventId);
                ack(handler.group(), record);
                return true;
            } catch (Exception ex) {
                log.warn("handler {} failed eventId={} attempt {}/{}: {}", handler.group(), eventId, attempt, maxAttempts, ex.toString());
                if (attempt < maxAttempts && !pause(backoffMs(attempt))) {
                    return false;   // interrupted: stop, stay pending
                }
            }
        }
        if (giveUpNow) {                           // only the sweeper, with the real cross-restart count, sets this true
            deadLetter.publish(handler.group(), record, "exceeded attempts");
            ack(handler.group(), record);
            return true;
        }
        return false;                              // left pending; the sweeper's XPENDING count decides DLQ later
    }

    long backoffCap(int attempt) {
        return Math.min(maxDelayMs, baseDelayMs * (1L << Math.min(attempt - 1, 20)));
    }

    long backoffMs(int attempt) {
        long cap = backoffCap(attempt);
        return ThreadLocalRandom.current().nextLong(cap / 2, cap + 1);
    }

    private boolean pause(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void ack(String group, MapRecord<String, String, String> record) {
        redis.opsForStream().acknowledge(stream, group, record.getId());
    }
}
