package com.ticketrush.organizer;

import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.organizer.dto.OrganizerShowStatsResponse;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizer")
public class OrganizerController {

    private final ShowRepository showRepository;
    private final JdbcTemplate jdbcTemplate;

    public OrganizerController(ShowRepository showRepository, JdbcTemplate jdbcTemplate) {
        this.showRepository = showRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/shows/{id}/stats")
    @PreAuthorize("hasRole('ADMIN') or @ownership.isShowOwner(#id, authentication)")
    @Transactional(readOnly = true)
    public OrganizerShowStatsResponse getShowStats(@PathVariable Long id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + id));
        Long eventId = show.getEvent().getId();
        String eventTitle = show.getEvent().getTitle();
        Long organizerId = show.getEvent().getOrganizer().getId();

        Map<String, Object> seatStats = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total_seats,
                       COALESCE(SUM(CASE WHEN status = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS available_seats,
                       COALESCE(SUM(CASE WHEN status = 'HELD' THEN 1 ELSE 0 END), 0) AS held_seats,
                       COALESCE(SUM(CASE WHEN status = 'SOLD' THEN 1 ELSE 0 END), 0) AS sold_seats
                  FROM seats
                 WHERE show_id = ?
                """, id);

        Map<String, Object> bookingStats = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS confirmed_bookings,
                       COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_bookings,
                       COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN total_cents ELSE 0 END), 0) AS confirmed_revenue_cents
                  FROM bookings
                 WHERE show_id = ?
                """, id);

        Map<String, Object> ticketStats = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN status = 'VALID' THEN 1 ELSE 0 END), 0) AS valid_tickets,
                       COALESCE(SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END), 0) AS used_tickets,
                       COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_tickets
                  FROM tickets
                 WHERE show_id = ?
                """, id);

        long[] organizerTotals = loadOrganizerTotals(organizerId);

        return new OrganizerShowStatsResponse(
                id,
                eventId,
                eventTitle,
                toLong(seatStats.get("total_seats")),
                toLong(seatStats.get("available_seats")),
                toLong(seatStats.get("held_seats")),
                toLong(seatStats.get("sold_seats")),
                toLong(bookingStats.get("confirmed_bookings")),
                toLong(bookingStats.get("cancelled_bookings")),
                toLong(bookingStats.get("confirmed_revenue_cents")),
                toLong(ticketStats.get("valid_tickets")),
                toLong(ticketStats.get("used_tickets")),
                toLong(ticketStats.get("cancelled_tickets")),
                organizerTotals[0],
                organizerTotals[1]
        );
    }

    private long[] loadOrganizerTotals(Long organizerId) {
        try {
            var rows = jdbcTemplate.queryForList("""
                    SELECT total_tickets_sold, total_revenue_cents
                      FROM organizer_sales_summary
                     WHERE organizer_id = ?
                    """, organizerId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                return new long[]{
                        toLong(row.get("total_tickets_sold")),
                        toLong(row.get("total_revenue_cents"))
                };
            }
        } catch (Exception ignored) {
            // Fall back to transactional tables when organizer_sales_summary is not present (e.g. H2 unit profile)
        }

        Map<String, Object> fallback = jdbcTemplate.queryForMap("""
                SELECT COUNT(t.id) AS total_tickets,
                       COALESCE((
                           SELECT SUM(b2.total_cents)
                             FROM bookings b2
                             JOIN shows s2 ON s2.id = b2.show_id
                             JOIN events e2 ON e2.id = s2.event_id
                            WHERE e2.organizer_id = ? AND b2.status = 'CONFIRMED'
                       ), 0) AS total_revenue_cents
                  FROM bookings b
                  JOIN shows s ON s.id = b.show_id
                  JOIN events e ON e.id = s.event_id
                  LEFT JOIN tickets t ON t.booking_id = b.id
                 WHERE e.organizer_id = ? AND b.status = 'CONFIRMED'
                """, organizerId, organizerId);
        return new long[]{
                toLong(fallback.get("total_tickets")),
                toLong(fallback.get("total_revenue_cents"))
        };
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
