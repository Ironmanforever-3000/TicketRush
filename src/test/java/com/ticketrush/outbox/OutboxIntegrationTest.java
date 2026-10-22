package com.ticketrush.outbox;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.booking.BookingCreationResult;
import com.ticketrush.booking.BookingRequest;
import com.ticketrush.booking.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.outbox.publisher-enabled=true"
})
class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private StringRedisTemplate redis;

    @Value("${app.events.stream}")
    private String stream;

    private Long testVenueId = 99998L;
    private Long testEventId = 99998L;
    private Long testShowId = 99998L;
    private Long testTierId = 99998L;
    private Long testUserId = 99997L;
    private Long testHoldId = 99998L;

    private Long seatId1 = 988042L;

    @BeforeEach
    void setupTestData() {
        cleanupTestData();

        jdbc.update("INSERT INTO users (id, name, email, password_hash, role, created_at, updated_at, is_active) VALUES (?, 'Test User', 'user99998@test.local', 'test', 'CUSTOMER', now(), now(), true)", testUserId);
        jdbc.update("INSERT INTO venues (id, name, city, layout_json, created_at) VALUES (?, 'Test Venue', 'City', '{}', now())", testVenueId);
        jdbc.update("INSERT INTO events (id, organizer_id, title, category, status, created_at) VALUES (?, ?, 'Event', 'Cat', 'DRAFT', now())", testEventId, testUserId);
        jdbc.update("INSERT INTO shows (id, event_id, venue_id, starts_at, sale_opens_at, status) VALUES (?, ?, ?, now(), now(), 'SCHEDULED')", testShowId, testEventId, testVenueId);
        jdbc.update("INSERT INTO seat_tiers (id, show_id, name, price_cents, currency) VALUES (?, ?, 'Standard', 1000, 'USD')", testTierId, testShowId);

        String seatsJson = String.format("[%d]", seatId1);
        jdbc.update("INSERT INTO holds (id, show_id, user_id, seat_ids, status, expires_at, created_at) VALUES (?, ?, ?, ?, 'ACTIVE', DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)",
                testHoldId, testShowId, testUserId, seatsJson);

        jdbc.update("INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, 'A', 1, 'HELD', ?, DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), 0)",
                seatId1, testShowId, testTierId, testUserId);
    }

    @AfterEach
    void cleanupTestData() {
        jdbc.update("DELETE FROM outbox WHERE aggregate_type = 'BOOKING'");
        jdbc.update("DELETE FROM tickets WHERE show_id = ?", testShowId);
        jdbc.update("DELETE FROM booking_seats WHERE seat_id = ?", seatId1);
        jdbc.update("DELETE FROM bookings WHERE show_id = ?", testShowId);
        jdbc.update("DELETE FROM holds WHERE show_id = ?", testShowId);
        jdbc.update("DELETE FROM seats WHERE id = ?", seatId1);
        jdbc.update("DELETE FROM seat_tiers WHERE show_id = ?", testShowId);
        jdbc.update("DELETE FROM shows WHERE id = ?", testShowId);
        jdbc.update("DELETE FROM events WHERE id = ?", testEventId);
        jdbc.update("DELETE FROM venues WHERE id = ?", testVenueId);
        jdbc.update("DELETE FROM users WHERE id = ?", testUserId);
    }

    @Test
    void testBookingCreatesOutbox() {
        UUID key = UUID.randomUUID();
        BookingCreationResult result = bookingService.createBooking(testUserId, key, new BookingRequest(testShowId, List.of(seatId1)));

        List<OutboxEvent> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(event.getAggregateType()).isEqualTo("BOOKING");
        assertThat(event.getAggregateId()).isEqualTo(result.response().bookingId());
        assertThat(event.getPublishedAt()).isNull();

        assertThat(event.getPayload()).contains(String.valueOf(result.response().bookingId()));
        assertThat(event.getPayload()).contains(String.valueOf(testShowId));
    }

    @Test
    void testPublisherPublishesEventAndUpdatesPublishedAt() {
        UUID key = UUID.randomUUID();
        BookingCreationResult result = bookingService.createBooking(testUserId, key, new BookingRequest(testShowId, List.of(seatId1)));

        outboxPublisher.publishBatch();

        List<OutboxEvent> all = outboxRepository.findAll();
        assertThat(all.get(0).getPublishedAt()).isNotNull();

        List<MapRecord<String, Object, Object>> records = redis.opsForStream().range(stream, org.springframework.data.domain.Range.unbounded());
        assertThat(records).isNotEmpty();
        boolean found = records.stream().anyMatch(r ->
                String.valueOf(result.response().bookingId()).equals(r.getValue().get("aggregate_id"))
        );
        assertThat(found).isTrue();
    }
}
