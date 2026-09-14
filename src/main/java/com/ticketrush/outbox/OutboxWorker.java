package com.ticketrush.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxService outboxService;

    public OutboxWorker(OutboxRepository outboxRepository, OutboxPublisher outboxPublisher, OutboxService outboxService) {
        this.outboxRepository = outboxRepository;
        this.outboxPublisher = outboxPublisher;
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelay = 5_000)
    public void publishOutboxEvents() {
        List<OutboxEvent> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        
        if (!events.isEmpty()) {
            log.info("Outbox worker found {} unpublished events", events.size());
        }

        for (OutboxEvent event : events) {
            try {
                // 1. Publish external event
                outboxPublisher.publish(event);

                // 2. Mark as published in isolated transaction
                outboxService.markPublished(event.getId());

                log.info("Successfully published outbox event {}", event.getId());
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {}", event.getId(), ex);
            }
        }
    }
}
