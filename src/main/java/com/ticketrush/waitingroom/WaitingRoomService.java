package com.ticketrush.waitingroom;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class WaitingRoomService {

    private final StringRedisTemplate redisTemplate;
    private final WaitingRoomTokenService tokenService;

    public WaitingRoomService(StringRedisTemplate redisTemplate, WaitingRoomTokenService tokenService) {
        this.redisTemplate = redisTemplate;
        this.tokenService = tokenService;
    }

    public long joinQueue(Long showId, Long userId) {
        String counterKey = "ticketrush:waiting-room:counter:" + showId;
        String queueKey = "ticketrush:waiting-room:queue:" + showId;

        Long sequence = redisTemplate.opsForValue().increment(counterKey);
        if (sequence == null) {
            sequence = 1L;
        }

        redisTemplate.opsForZSet().add(queueKey, String.valueOf(userId), sequence.doubleValue());
        return sequence;
    }

    public Long getQueuePosition(Long showId, Long userId) {
        String queueKey = "ticketrush:waiting-room:queue:" + showId;
        Long rank = redisTemplate.opsForZSet().rank(queueKey, String.valueOf(userId));
        return rank != null ? rank + 1 : null;
    }

    public List<AdmittedUser> admitNextBatch(Long showId, int count) {
        String queueKey = "ticketrush:waiting-room:queue:" + showId;
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(queueKey, count);
        if (popped == null || popped.isEmpty()) {
            return List.of();
        }

        List<AdmittedUser> admitted = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : popped) {
            if (tuple.getValue() == null || tuple.getScore() == null) continue;
            Long userId = Long.parseLong(tuple.getValue());
            long queueNumber = tuple.getScore().longValue();
            String token = tokenService.issueToken(showId, userId, queueNumber, Duration.ofMinutes(10));
            admitted.add(new AdmittedUser(userId, queueNumber, token));
        }
        return admitted;
    }

    public boolean validateToken(String token, Long showId, Long userId) {
        return tokenService.validateAndConsumeToken(token, showId, userId);
    }

    public record AdmittedUser(Long userId, long queueNumber, String token) {}
}
