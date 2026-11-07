package com.ticketrush.websocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!worker")
public class WebSocketMetrics {
    public WebSocketMetrics(MeterRegistry registry, ConnectionTracker tracker) {
        Gauge.builder("ticketrush.websocket.connections", tracker, ConnectionTracker::count)
                .description("Active WebSocket sessions on this instance")
                .register(registry);
    }
}
