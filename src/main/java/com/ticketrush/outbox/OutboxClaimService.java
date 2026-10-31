package com.ticketrush.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxClaimService {
    private final OutboxRepository outbox;

    public OutboxClaimService(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public int claim(String batchToken, int leaseSeconds, int batchSize) {
        return outbox.claimBatch(batchToken, leaseSeconds, batchSize);
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findClaimed(String batchToken) {
        return outbox.findByClaimedByAndPublishedAtIsNull(batchToken);
    }

    @Transactional
    public void markPublished(Long id) {
        outbox.markPublished(id, OffsetDateTime.now());
    }
}
