package com.ticketrush.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ticketrush.exception.IdempotencyKeyReusedException;
import com.ticketrush.seat.SeatUnavailableException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testVenueId = 99999L;
    private Long testEventId = 99999L;
    private Long testShowId = 99999L;
    private Long testTierId = 99999L;
    private Long testUserId = 99997L; // Matching controller if needed
    private Long testOtherUserId = 99998L;
    private Long testHoldId = 99999L;

    // Use very high unique IDs to prevent DEV database clashes
    private Long s1 = 999042L;
    private Long s2 = 999043L;
    private Long s3 = 999044L;
    private Long s4 = 999045L;
    private Long s5 = 999046L;

    @BeforeEach
    void setupTestData() {
        cleanupTestData();

        jdbcTemplate.update("INSERT INTO users (id, name, email) VALUES (?, 'Test User', 'user99999@test.local')", testUserId);
        jdbcTemplate.update("INSERT INTO users (id, name, email) VALUES (?, 'Other User', 'other99999@test.local')", testOtherUserId);
        jdbcTemplate.update("INSERT INTO venues (id, name, city, layout_json, created_at) VALUES (?, 'Test Venue', 'City', '{}', now())", testVenueId);
        jdbcTemplate.update("INSERT INTO events (id, organizer_id, title, category, status, created_at) VALUES (?, ?, 'Event', 'Cat', 'DRAFT', now())", testEventId, testUserId);
        jdbcTemplate.update("INSERT INTO shows (id, event_id, venue_id, starts_at, sale_opens_at, status) VALUES (?, ?, ?, now(), now(), 'SCHEDULED')", testShowId, testEventId, testVenueId);
        jdbcTemplate.update("INSERT INTO seat_tiers (id, show_id, name, price_cents, currency) VALUES (?, ?, 'Standard', 1000, 'USD')", testTierId, testShowId);
        
        String seatsJson = String.format("[%d, %d, %d, %d]", s1, s2, s3, s4);
        jdbcTemplate.update("INSERT INTO holds (id, show_id, user_id, seat_ids, status, expires_at, created_at) VALUES (?, ?, ?, ?::jsonb, 'ACTIVE', now() + interval '10 minutes', now())", 
            testHoldId, testShowId, testUserId, seatsJson);

        // Standard setup: Seats s1, s2 HELD by user
        jdbcTemplate.update("INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, 'A', 1, 'HELD', ?, now() + interval '10 minutes', 0)", s1, testShowId, testTierId, testUserId);
        jdbcTemplate.update("INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, 'A', 2, 'HELD', ?, now() + interval '10 minutes', 0)", s2, testShowId, testTierId, testUserId);
        
        // s3 is AVAILABLE
        jdbcTemplate.update("INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, 'A', 3, 'AVAILABLE', NULL, NULL, 0)", s3, testShowId, testTierId);
        
        // s4 is HELD but expired
        jdbcTemplate.update("INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, 'A', 4, 'HELD', ?, now() - interval '10 minutes', 0)", s4, testShowId, testTierId, testUserId);

        // s5 is SOLD
        jdbcTemplate.update("INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, 'A', 5, 'SOLD', ?, now() + interval '10 minutes', 0)", s5, testShowId, testTierId, testUserId);
    }

    @AfterEach
    void cleanupTestData() {
        // Use IN clause directly for all our test seats
        jdbcTemplate.update("DELETE FROM outbox WHERE aggregate_type = 'BOOKING' AND payload::text LIKE '%99999%'");
        jdbcTemplate.update("DELETE FROM booking_seats WHERE seat_id IN (?, ?, ?, ?, ?)", s1, s2, s3, s4, s5);
        jdbcTemplate.update("DELETE FROM bookings WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM holds WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM seats WHERE id IN (?, ?, ?, ?, ?)", s1, s2, s3, s4, s5);
        jdbcTemplate.update("DELETE FROM seat_tiers WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM shows WHERE id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM events WHERE id = ?", testEventId);
        jdbcTemplate.update("DELETE FROM venues WHERE id = ?", testVenueId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", testUserId, testOtherUserId);
    }

    @Test
    void testSuccessfulBooking() {
        UUID idempotencyKey = UUID.randomUUID();
        BookingRequest request = new BookingRequest(testShowId, List.of(s1, s2));

        BookingCreationResult result = bookingService.createBooking(testUserId, idempotencyKey, request);
        
        assertThat(result.created()).isTrue();
        assertThat(result.response().status()).isEqualTo("CONFIRMED");
        
        // Verify DB
        Integer seatCount = jdbcTemplate.queryForObject("SELECT count(*) FROM seats WHERE status = 'SOLD' AND id IN (?, ?)", Integer.class, s1, s2);
        assertThat(seatCount).isEqualTo(2);

        Integer outboxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_type = 'BOOKING' AND aggregate_id = ?", Integer.class, result.response().bookingId());
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void testDuplicateRetryReturns200() {
        UUID idempotencyKey = UUID.randomUUID();
        BookingRequest request = new BookingRequest(testShowId, List.of(s1));

        BookingCreationResult first = bookingService.createBooking(testUserId, idempotencyKey, request);
        BookingCreationResult second = bookingService.createBooking(testUserId, idempotencyKey, request);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(first.response().bookingId()).isEqualTo(second.response().bookingId());
    }

    @Test
    void testConcurrentSameKey() throws InterruptedException {
        UUID idempotencyKey = UUID.randomUUID();
        BookingRequest request = new BookingRequest(testShowId, List.of(s1));
        
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        
        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger okCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    BookingCreationResult result = bookingService.createBooking(testUserId, idempotencyKey, request);
                    if (result.created()) createdCount.incrementAndGet();
                    else okCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        
        latch.countDown();
        done.await();
        
        assertThat(createdCount.get()).isEqualTo(1);
        assertThat(createdCount.get() + okCount.get()).isEqualTo(threads);
        
        Integer bookingCount = jdbcTemplate.queryForObject("SELECT count(*) FROM bookings WHERE idempotency_key = ?", Integer.class, idempotencyKey);
        assertThat(bookingCount).isEqualTo(1);
    }

    @Test
    void testPartialSeatFailureRollsBack() {
        UUID idempotencyKey = UUID.randomUUID();
        // s3 is AVAILABLE, not HELD. Should fail completely.
        BookingRequest request = new BookingRequest(testShowId, List.of(s1, s3));

        assertThatThrownBy(() -> bookingService.createBooking(testUserId, idempotencyKey, request))
            .isInstanceOf(SeatUnavailableException.class);
            
        // Assert s1 did not get sold
        String statusS1 = jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, s1);
        assertThat(statusS1).isEqualTo("HELD");
    }

    @Test
    void testExpiredHold() {
        UUID idempotencyKey = UUID.randomUUID();
        BookingRequest request = new BookingRequest(testShowId, List.of(s4)); // s4 is expired

        assertThatThrownBy(() -> bookingService.createBooking(testUserId, idempotencyKey, request))
            .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void testWrongUser() {
        UUID idempotencyKey = UUID.randomUUID();
        BookingRequest request = new BookingRequest(testShowId, List.of(s1)); 

        assertThatThrownBy(() -> bookingService.createBooking(testOtherUserId, idempotencyKey, request))
            .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void testIdempotencyKeyReused() {
        UUID idempotencyKey = UUID.randomUUID();
        BookingRequest request1 = new BookingRequest(testShowId, List.of(s1));
        bookingService.createBooking(testUserId, idempotencyKey, request1);

        BookingRequest request2 = new BookingRequest(testShowId, List.of(s2));
        
        assertThatThrownBy(() -> bookingService.createBooking(testUserId, idempotencyKey, request2))
            .isInstanceOf(IdempotencyKeyReusedException.class);
    }
}
