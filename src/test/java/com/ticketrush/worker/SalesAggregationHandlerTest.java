package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SalesAggregationHandlerTest extends AbstractWorkerIntegrationTest {
    @Autowired SalesAggregationHandler handler;
    @Autowired JdbcTemplate jdbc;

    @Test
    void incrementsOrganizerTotalsOnceDespiteRedelivery() throws Exception {
        WorkerFixture f = bookedShow(3);
        var event = confirmedEventFor(f);
        handler.handle(event);
        handler.handle(event);
        Long sold = jdbc.queryForObject(
                "SELECT total_tickets_sold FROM organizer_sales_summary WHERE organizer_id = ?", Long.class, f.organizerId());
        assertThat(sold).isEqualTo(3);
    }
}
