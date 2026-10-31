package com.ticketrush.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisIdempotencyHintTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisIdempotencyHint hint;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        hint = new RedisIdempotencyHint(redis);
        ReflectionTestUtils.setField(hint, "ttlDays", 7);
    }

    @Test
    void probablyDoneReturnsTrueWhenKeyExists() {
        when(redis.hasKey("idempotency:ticket-generation:123")).thenReturn(true);
        assertThat(hint.probablyDone("ticket-generation", 123L)).isTrue();
    }

    @Test
    void probablyDoneReturnsFalseWhenKeyMissing() {
        when(redis.hasKey("idempotency:ticket-generation:123")).thenReturn(false);
        assertThat(hint.probablyDone("ticket-generation", 123L)).isFalse();
    }

    @Test
    void probablyDoneFailsOpenToFalseOnRedisException() {
        when(redis.hasKey("idempotency:ticket-generation:123"))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));
        assertThat(hint.probablyDone("ticket-generation", 123L)).isFalse();
    }

    @Test
    void markDoneWritesKeyWithTtl() {
        hint.markDone("email-notification", 456L);
        verify(valueOps).set(eq("idempotency:email-notification:456"), eq("DONE"), eq(Duration.ofDays(7)));
    }

    @Test
    void markDoneFailsQuietlyOnRedisException() {
        doThrow(new RedisConnectionFailureException("Connection refused"))
                .when(valueOps).set(any(), any(), any(Duration.class));
        assertThatCode(() -> hint.markDone("email-notification", 456L)).doesNotThrowAnyException();
    }
}
