package com.ticketrush.websocket;

import com.ticketrush.realtime.RedisSeatSubscriber;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class RedisSeatSubscriberResilienceTest {

    @Test
    void malformedMessageIsLoggedNotThrown() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        RedisSeatSubscriber subscriber = new RedisSeatSubscriber(new ObjectMapper(), messagingTemplate, new SimpleMeterRegistry());

        assertThatCode(() -> subscriber.onMessage(new DefaultMessage("ch".getBytes(), "not json".getBytes()), null))
                .doesNotThrowAnyException();
    }
}
