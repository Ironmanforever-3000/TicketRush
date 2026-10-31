package com.ticketrush.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Service
public class EmailNotificationHandler implements StreamEventHandler {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationHandler.class);
    private static final String GROUP = "email-notification";
    private final ProcessedMessageRepository processed;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EmailNotificationHandler(ProcessedMessageRepository processed, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.processed = processed;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("BOOKING_CONFIRMED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(StreamEvent event) throws Exception {
        long eventId = Long.parseLong(event.eventId());
        if (!processed.tryInsert(GROUP, eventId)) {
            return;          // already handled
        }

        long bookingId = mapper.readTree(event.payload()).get("booking_id").asLong();
        String email = jdbc.queryForObject(
                "SELECT u.email FROM bookings b JOIN users u ON u.id = b.user_id WHERE b.id = ?", String.class, bookingId);
        int seats = mapper.readTree(event.payload()).get("seat_ids").size();

        log.info("SIMULATED_EMAIL bookingId={} recipient={} seats={}", bookingId, email, seats);
        // A real provider call is its own retry-safe step after this commits — this marker
        // guarantees the INTENT was recorded once, never that a real send happened once.
    }
}
