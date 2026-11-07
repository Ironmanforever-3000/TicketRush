package com.ticketrush.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimitService {
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List<Long>> tokenBucketScript;

    @SuppressWarnings("unchecked")
    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        this.tokenBucketScript.setResultType((Class<List<Long>>) (Class<?>) List.class);
    }

    public RateLimitResult consume(String key, RateLimitPolicy policy) {
        List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(policy.capacity()),
                String.valueOf(policy.refillRate()),
                String.valueOf(policy.requestCost())
        );

        if (result == null || result.size() != 4) {
            throw new IllegalStateException("Invalid rate limiter response from Redis");
        }

        return new RateLimitResult(
                ((Number) result.get(0)).longValue() == 1,
                ((Number) result.get(1)).longValue(),
                ((Number) result.get(2)).longValue(),
                ((Number) result.get(3)).longValue()
        );
    }
}
