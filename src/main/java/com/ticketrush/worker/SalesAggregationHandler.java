package com.ticketrush.worker;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Service
public class SalesAggregationHandler implements StreamEventHandler {
    private static final String GROUP = "sales-aggregation";
    private final ProcessedMessageRepository processed;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SalesAggregationHandler(ProcessedMessageRepository processed,
                                   JdbcTemplate jdbc,
                                   ObjectMapper mapper) {
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
            return;
        }

        JsonNode payload = mapper.readTree(event.payload());
        long showId = payload.get("show_id").asLong();
        int seatCount = payload.get("seat_ids").size();
        long amountCents = payload.get("total_cents").asLong();

        Long organizerId = jdbc.queryForObject(
                "SELECT e.organizer_id FROM shows s JOIN events e ON e.id = s.event_id WHERE s.id = ?", Long.class, showId);

        jdbc.update("""
            INSERT INTO organizer_sales_summary (organizer_id, total_tickets_sold, total_revenue_cents, updated_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (organizer_id) DO UPDATE
               SET total_tickets_sold  = organizer_sales_summary.total_tickets_sold  + EXCLUDED.total_tickets_sold,
                   total_revenue_cents = organizer_sales_summary.total_revenue_cents + EXCLUDED.total_revenue_cents,
                   updated_at = now()""", organizerId, seatCount, amountCents);
    }
}
