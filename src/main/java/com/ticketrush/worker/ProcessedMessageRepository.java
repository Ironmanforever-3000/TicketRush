package com.ticketrush.worker;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedMessageRepository {
    private final JdbcTemplate jdbc;

    public ProcessedMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** true = this call claimed the event (first processing). false = another transaction
     *  already committed it for this consumer. */
    public boolean tryInsert(String consumerName, long eventId) {
        int inserted = jdbc.update("""
            INSERT INTO processed_messages (consumer_name, event_id, processed_at)
            VALUES (?, ?, now()) ON CONFLICT (consumer_name, event_id) DO NOTHING""",
            consumerName, eventId);
        return inserted == 1;
    }
}
