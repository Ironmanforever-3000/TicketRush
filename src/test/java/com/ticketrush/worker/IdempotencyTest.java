package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyTest extends AbstractWorkerIntegrationTest {
    @Autowired TicketQrHandler ticketHandler;
    @Autowired EmailNotificationHandler emailHandler;
    @Autowired SalesAggregationHandler salesHandler;
    @Autowired JdbcTemplate jdbc;

    @Test
    void sameEventIsValidOnceForEachGroupIndependently() throws Exception {
        var event = confirmedEventFor(bookedShow(2));
        ticketHandler.handle(event);
        emailHandler.handle(event);
        salesHandler.handle(event);

        Long marks = jdbc.queryForObject(
                "SELECT count(*) FROM processed_messages WHERE event_id = ?", Long.class, Long.parseLong(event.eventId()));
        assertThat(marks).isEqualTo(3);   // one row per consumer group, same event_id
    }

    @Test
    void concurrentDuplicateDeliveriesDoNotDoubleCountSales() throws Exception {
        var f = bookedShow(3);
        var event = confirmedEventFor(f);
        var pool = Executors.newFixedThreadPool(8);
        var go = new CountDownLatch(1);
        var futures = IntStream.range(0, 8).mapToObj(i -> pool.submit(() -> {
            try { go.await(); salesHandler.handle(event); } catch (Exception ignored) {}
        })).toList();
        go.countDown();
        for (var fut : futures) fut.get();
        pool.shutdown();

        Long sold = jdbc.queryForObject(
                "SELECT total_tickets_sold FROM organizer_sales_summary WHERE organizer_id = ?", Long.class, f.organizerId());
        assertThat(sold).isEqualTo(3);    // not 24
    }

    @Test
    void aFailedAttemptRollsBackItsMarkerSoARetryCanSucceedLater() {
        var bad = new StreamEvent("424242", "BOOKING_CONFIRMED", "{\"booking_id\":999999999}", Instant.now());
        assertThatThrownBy(() -> salesHandler.handle(bad)).isInstanceOf(Exception.class);

        Long marked = jdbc.queryForObject(
                "SELECT count(*) FROM processed_messages WHERE consumer_name='sales-aggregation' AND event_id = 424242", Long.class);
        assertThat(marked).isZero();      // rolled back with the rest of the failed transaction - retryable
    }
}
