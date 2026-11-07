package com.ticketrush.outbox;

import com.ticketrush.realtime.RedisSeatPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxBatchServiceTest {
    OutboxClaimService claims = mock(OutboxClaimService.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
    RedisSeatPublisher seatPublisher = mock(RedisSeatPublisher.class);
    OutboxBatchService service;

    @BeforeEach
    void setUp() {
        service = new OutboxBatchService(claims, redis, seatPublisher);
        ReflectionTestUtils.setField(service, "stream", "ticketrush:events");
        ReflectionTestUtils.setField(service, "batchSize", 100);
        ReflectionTestUtils.setField(service, "leaseSeconds", 30);
    }

    private OutboxEvent outboxEvent(Long id) {
        OutboxEvent e = new OutboxEvent();
        e.setId(id);
        e.setEventType("BOOKING_CONFIRMED");
        e.setPayload("{\"booking_id\":" + id + "}");
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }

    @Test
    void publishesAllClaimedEventsAndMarksEachPublished() {
        var e1 = outboxEvent(1L);
        var e2 = outboxEvent(2L);
        when(claims.claim(any(), anyInt(), anyInt())).thenReturn(2);
        when(claims.findClaimed(any())).thenReturn(List.of(e1, e2));

        assertThat(service.publishBatch()).isEqualTo(2);
        verify(claims).markPublished(1L);
        verify(claims).markPublished(2L);
    }

    @Test
    void seatStateChangedEventGoesToPubSubPublisherNotStream() {
        OutboxEvent e = new OutboxEvent();
        e.setId(10L);
        e.setEventType("SEAT_STATE_CHANGED");
        e.setPayload("{\"status\":\"HELD\"}");
        e.setCreatedAt(OffsetDateTime.now());

        when(claims.claim(any(), anyInt(), anyInt())).thenReturn(1);
        when(claims.findClaimed(any())).thenReturn(List.of(e));

        assertThat(service.publishBatch()).isEqualTo(1);
        verify(seatPublisher).publish("{\"status\":\"HELD\"}");
        verify(claims).markPublished(10L);
        verifyNoInteractions(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsOnFirstRedisFailureAndMarksNothingPublished() {
        var e1 = outboxEvent(1L);
        var e2 = outboxEvent(2L);
        when(claims.claim(any(), anyInt(), anyInt())).thenReturn(2);
        when(claims.findClaimed(any())).thenReturn(List.of(e1, e2));
        when(redis.opsForStream().add(any(MapRecord.class))).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(service.publishBatch()).isZero();
        verify(claims, never()).markPublished(any());
    }

    @Test
    void noUnpublishedRowsIsANoOp() {
        when(claims.claim(any(), anyInt(), anyInt())).thenReturn(0);
        assertThat(service.publishBatch()).isZero();
        verifyNoInteractions(redis);
    }
}
