package com.ticketrush.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!worker")
public class OutboxPollingJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxPollingJob.class);
    private final OutboxBatchService batchService;

    public OutboxPollingJob(OutboxBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:500}")
    public void poll() {
        try {
            int n = batchService.publishBatch();
            if (n > 0) log.debug("Published {} outbox events", n);
        } catch (Exception ex) {
            log.error("Outbox publication failed; will retry on next tick", ex);
        }
    }
}
