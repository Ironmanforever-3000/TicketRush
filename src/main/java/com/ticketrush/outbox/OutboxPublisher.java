package com.ticketrush.outbox;

public interface OutboxPublisher {
    void publish(OutboxEvent event);
}
