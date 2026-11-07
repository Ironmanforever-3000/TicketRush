package com.ticketrush.websocket;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketMetricsTest {

    @Test
    void gaugeReflectsTrackerCount() {
        var registry = new SimpleMeterRegistry();
        var tracker = new ConnectionTracker();
        new WebSocketMetrics(registry, tracker);
        tracker.connected("s1");
        tracker.connected("s2");
        assertThat(registry.get("ticketrush.websocket.connections").gauge().value()).isEqualTo(2.0);
    }
}
