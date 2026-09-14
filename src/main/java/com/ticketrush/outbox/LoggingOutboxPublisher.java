package com.ticketrush.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        log.info(
            "Publishing outbox event id={} type={} aggregateType={} aggregateId={} payload={}",
            event.getId(),
            event.getEventType(),
            event.getAggregateType(),
            event.getAggregateId(),
            event.getPayload()
        );
    }
}
