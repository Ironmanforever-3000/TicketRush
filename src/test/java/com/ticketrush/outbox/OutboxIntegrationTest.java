package com.ticketrush.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.ticketrush.booking.BookingRequest;
import com.ticketrush.booking.BookingService;
import com.ticketrush.booking.BookingCreationResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = { "spring.main.allow-bean-definition-overriding=true" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private TestOutboxPublisher testPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testVenueId = 99998L;
    private Long testEventId = 99998L;
    private Long testShowId = 99998L;
    private Long testTierId = 99998L;
    private Long testUserId = 99997L;
    private Long testHoldId = 99998L;

    private Long seatId1 = 988042L;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public TestOutboxPublisher testOutboxPublisher() {
            return new TestOutboxPublisher();
        }
    }

    static class TestOutboxPublisher implements OutboxPublisher {
        public OutboxEvent captured;
        public boolean throwException = false;

        @Override
        public void publish(OutboxEvent event) {
            if (throwException) {
                throw new RuntimeException("Simulated failure");
            }
            this.captured = event;
        }

        public void reset() {
            this.captured = null;
            this.throwException = false;
        }
    }

    @BeforeEach
    void setupTestData() {
        cleanupTestData();
        testPublisher.reset();

        jdbcTemplate.update("INSERT INTO users (id, name, email, password_hash, role, created_at, updated_at, is_active) VALUES (?, 'Test User', 'user99998@test.local', 'test', 'CUSTOMER', now(), now(), true)", testUserId);
        jdbcTemplate.update("INSERT INTO venues (id, name, city, layout_json, created_at) VALUES (?, 'Test Venue', 'City', '{}', now())", testVenueId);
        jdbcTemplate.update("INSERT INTO events (id, organizer_id, title, category, status, created_at) VALUES (?, ?, 'Event', 'Cat', 'DRAFT', now())", testEventId, testUserId);
        jdbcTemplate.update("INSERT INTO shows (id, event_id, venue_id, starts_at, sale_opens_at, status) VALUES (?, ?, ?, now(), now(), 'SCHEDULED')", testShowId, testEventId, testVenueId);
        jdbcTemplate.update("INSERT INTO seat_tiers (id, show_id, name, price_cents, currency) VALUES (?, ?, 'Standard', 1000, 'USD')", testTierId, testShowId);
        
        String seatsJson = String.format("[%d]", seatId1);
        jdbcTemplate.update("INSERT INTO holds (id, show_id, user_id, seat_ids, status, expires_at, created_at) VALUES (?, ?, ?, ?, 'ACTIVE', DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)", 
            testHoldId, testShowId, testUserId, seatsJson);

        jdbcTemplate.update("INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, 'A', 1, 'HELD', ?, DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), 0)", seatId1, testShowId, testTierId, testUserId);
    }

    @AfterEach
    void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM outbox WHERE aggregate_type = 'BOOKING'");
        jdbcTemplate.update("DELETE FROM booking_seats WHERE seat_id = ?", seatId1);
        jdbcTemplate.update("DELETE FROM bookings WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM holds WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM seats WHERE id = ?", seatId1);
        jdbcTemplate.update("DELETE FROM seat_tiers WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM shows WHERE id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM events WHERE id = ?", testEventId);
        jdbcTemplate.update("DELETE FROM venues WHERE id = ?", testVenueId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", testUserId);
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
        
        // Assert payload
        assertThat(event.getPayload()).contains(String.valueOf(result.response().bookingId()));
        assertThat(event.getPayload()).contains(String.valueOf(testShowId));
    }

    @Test
    void testWorkerPublishesEventAndUpdatesPublishedAt() {
        UUID key = UUID.randomUUID();
        bookingService.createBooking(testUserId, key, new BookingRequest(testShowId, List.of(seatId1)));
        
        outboxWorker.publishOutboxEvents();
        
        assertThat(testPublisher.captured).isNotNull();
        assertThat(testPublisher.captured.getEventType()).isEqualTo("BOOKING_CONFIRMED");
        
        List<OutboxEvent> all = outboxRepository.findAll();
        assertThat(all.get(0).getPublishedAt()).isNotNull();
    }

    @Test
    void testPublisherFailureLeavesEventUnpublished() {
        UUID key = UUID.randomUUID();
        bookingService.createBooking(testUserId, key, new BookingRequest(testShowId, List.of(seatId1)));
        
        testPublisher.throwException = true;
        outboxWorker.publishOutboxEvents();
        
        List<OutboxEvent> all = outboxRepository.findAll();
        assertThat(all.get(0).getPublishedAt()).isNull(); // Should still be null!
        
        // Retry logic test
        testPublisher.throwException = false;
        outboxWorker.publishOutboxEvents();
        
        all = outboxRepository.findAll();
        assertThat(all.get(0).getPublishedAt()).isNotNull(); // Succeeded on retry
    }
}
