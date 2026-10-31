package com.ticketrush.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Profile("worker")
public class ConsumerGroupInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupInitializer.class);
    private static final List<String> GROUPS = List.of("ticket-generation", "email-notification", "sales-aggregation");

    private final StringRedisTemplate redis;
    @Value("${app.events.stream}") private String stream;

    public ConsumerGroupInitializer(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void run(ApplicationArguments args) {
        GROUPS.forEach(this::ensureGroup);
    }

    public void ensureGroup(String group) {
        try {
            redis.execute((RedisCallback<String>) conn -> {
                conn.streamCommands().xGroupCreate(
                        stream.getBytes(StandardCharsets.UTF_8), group, ReadOffset.from("0"), true);
                return null;
            });
            log.info("Created consumer group {} on {}", group, stream);
        } catch (RuntimeException ex) {
            if (mostSpecificMessage(ex).contains("BUSYGROUP")) {
                log.debug("Consumer group {} already exists", group);
            } else {
                throw ex;
            }
        }
    }

    private String mostSpecificMessage(Throwable t) {
        return String.valueOf(NestedExceptionUtils.getMostSpecificCause(t).getMessage());
    }
}
