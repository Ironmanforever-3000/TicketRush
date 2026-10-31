package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNotificationHandlerTest extends AbstractWorkerIntegrationTest {
    @Autowired EmailNotificationHandler handler;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recordsExactlyOneNotificationPerEvent() throws Exception {
        var event = confirmedEventFor(bookedShow(1));
        handler.handle(event);
        handler.handle(event);
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM processed_messages WHERE consumer_name = 'email-notification' AND event_id = ?",
                Long.class, Long.parseLong(event.eventId()));
        assertThat(count).isEqualTo(1);
    }
}
