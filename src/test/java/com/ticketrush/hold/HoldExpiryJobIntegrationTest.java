package com.ticketrush.hold;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HoldExpiryJobIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HoldExpiryJob holdExpiryJob;

    private Long testVenueId = 9999L;
    private Long testEventId = 9999L;
    private Long testShowId = 9999L;
    private Long testTierId = 9999L;
    private Long testOrganizerId = 9999L;
    private Long testUserId = 9998L;

    @BeforeEach
    void setupTestData() {
        // Ensure clean state for test IDs
        cleanupTestData();
        
        // Add basic prerequisite data
        jdbcTemplate.update("INSERT INTO users (id, name, email) VALUES (?, 'Test Organizer', 'org9999@test.local')", testOrganizerId);
        jdbcTemplate.update("INSERT INTO users (id, name, email) VALUES (?, 'Test User', 'user9999@test.local')", testUserId);
        jdbcTemplate.update("INSERT INTO venues (id, name, city, layout_json, created_at) VALUES (?, 'Test Venue', 'City', '{}', now())", testVenueId);
        jdbcTemplate.update("INSERT INTO events (id, organizer_id, title, category, status, created_at) VALUES (?, ?, 'Event', 'Cat', 'DRAFT', now())", testEventId, testOrganizerId);
        jdbcTemplate.update("INSERT INTO shows (id, event_id, venue_id, starts_at, sale_opens_at, status) VALUES (?, ?, ?, now(), now(), 'SCHEDULED')", testShowId, testEventId, testVenueId);
        jdbcTemplate.update("INSERT INTO seat_tiers (id, show_id, name, price_cents, currency) VALUES (?, ?, 'Standard', 1000, 'USD')", testTierId, testShowId);

        // Insert seats to test different scenarios
        
        // 99042: Expired HELD seat
        jdbcTemplate.update(
            "INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, now() - interval '1 minute', 0)",
            99042L, testShowId, testTierId, "A", 1, "HELD", testUserId
        );
        
        // 99043: Non-expired HELD seat (future expiry)
        jdbcTemplate.update(
            "INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, now() + interval '10 minutes', 0)",
            99043L, testShowId, testTierId, "A", 2, "HELD", testUserId
        );
        
        // 99044: Expired SOLD seat
        jdbcTemplate.update(
            "INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, ?, ?, ?, NULL, now() - interval '10 minutes', 0)",
            99044L, testShowId, testTierId, "A", 3, "SOLD"
        );
        
        // 99045: Another expired HELD seat
        jdbcTemplate.update(
            "INSERT INTO seats (id, show_id, tier_id, row_label, seat_number, status, held_by, hold_expires_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, now() - interval '2 minutes', 0)",
            99045L, testShowId, testTierId, "A", 4, "HELD", testUserId
        );
    }

    @AfterEach
    void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM holds WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM seats WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM seat_tiers WHERE show_id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM shows WHERE id = ?", testShowId);
        jdbcTemplate.update("DELETE FROM events WHERE id = ?", testEventId);
        jdbcTemplate.update("DELETE FROM venues WHERE id = ?", testVenueId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", testOrganizerId, testUserId);
    }

    @Test
    void shouldReleaseExpiredHeldSeats() {
        // Run the job manually
        holdExpiryJob.expireHolds();

        // 1. Verify seat 99042 became AVAILABLE and properties cleared
        Map<String, Object> seat42 = jdbcTemplate.queryForMap(
                "SELECT status, held_by, hold_expires_at, version FROM seats WHERE id = ?", 99042L);
        assertThat(seat42.get("status")).isEqualTo("AVAILABLE");
        assertThat(seat42.get("held_by")).isNull();
        assertThat(seat42.get("hold_expires_at")).isNull();
        assertThat((Long) seat42.get("version")).isEqualTo(1L);

        // 2. Verify seat 99045 became AVAILABLE
        Map<String, Object> seat45 = jdbcTemplate.queryForMap(
                "SELECT status FROM seats WHERE id = ?", 99045L);
        assertThat(seat45.get("status")).isEqualTo("AVAILABLE");

        // 3. Verify seat 99043 remains HELD (future expiry)
        Map<String, Object> seat43 = jdbcTemplate.queryForMap(
                "SELECT status, held_by, hold_expires_at FROM seats WHERE id = ?", 99043L);
        assertThat(seat43.get("status")).isEqualTo("HELD");
        assertThat(seat43.get("held_by")).isNotNull();
        assertThat(seat43.get("hold_expires_at")).isNotNull();

        // 4. Verify seat 99044 remains SOLD
        Map<String, Object> seat44 = jdbcTemplate.queryForMap(
                "SELECT status FROM seats WHERE id = ?", 99044L);
        assertThat(seat44.get("status")).isEqualTo("SOLD");
    }
}
