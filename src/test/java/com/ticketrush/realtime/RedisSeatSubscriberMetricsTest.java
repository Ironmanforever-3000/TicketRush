package com.ticketrush.realtime;

import com.ticketrush.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.DefaultMessage;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSeatSubscriberMetricsTest extends AbstractIntegrationTest {

    @Autowired
    RedisSeatSubscriber subscriber;

    @Autowired
    MeterRegistry registry;

    @Test
    void malformedMessageIncrementsTheCounter() {
        double before = registry.get("ticketrush.websocket.seat_update.malformed").counter().count();
        subscriber.onMessage(new DefaultMessage("ch".getBytes(), "not json".getBytes()), null);
        assertThat(registry.get("ticketrush.websocket.seat_update.malformed").counter().count()).isEqualTo(before + 1);
    }
}
