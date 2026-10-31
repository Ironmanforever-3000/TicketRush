package com.ticketrush.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DeadLetterPublisher {
    private final StringRedisTemplate redis;
    @Value("${app.events.dead-letter-stream:ticketrush:dead-letter}")
    private String dlq;

    public DeadLetterPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publish(String group, MapRecord<String, String, String> record, String reason) {
        Map<String, String> f = new LinkedHashMap<>(record.getValue());   // preserves eventId, eventType, payload
        f.put("group", group);
        f.put("originalId", record.getId().getValue());
        f.put("reason", reason);
        f.put("deadLetteredAt", Instant.now().toString());
        redis.opsForStream().add(StreamRecords.newRecord().in(dlq).ofMap(f));
    }
}
