package com.ticketrush.realtime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisSeatPublisher {
    private static final String CHANNEL = "ticketrush:seat-updates";
    private final StringRedisTemplate redis;

    public RedisSeatPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publish(String payloadJson) {
        redis.convertAndSend(CHANNEL, payloadJson);
    }
}
