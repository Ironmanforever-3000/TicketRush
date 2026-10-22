package com.ticketrush.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    public static final AtomicLong skippedDuplicates = new AtomicLong();

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private final JdbcTemplate jdbc;

    @Value("${app.worker.idempotency-ttl-days:14}")
    private int ttlDays;

    public InboxService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(rollbackFor = Exception.class)
    public void runOnce(String group, String eventId, ThrowingRunnable work) throws Exception {
        int inserted = jdbc.update(
                "INSERT INTO processed_events(consumer_group, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING", group, eventId);
        if (inserted == 0) {
            skippedDuplicates.incrementAndGet();
            return;
        }
        work.run();
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void purgeOld() {
        try {
            int deleted = jdbc.update("DELETE FROM processed_events WHERE processed_at < now() - make_interval(days => ?)", ttlDays);
            log.info("Purged {} expired processed_events older than {} days", deleted, ttlDays);
        } catch (Exception e) {
            log.warn("Failed to purge old processed_events: {}", e.toString());
        }
    }
}
