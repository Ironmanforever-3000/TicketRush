package com.ticketrush.show;

import com.ticketrush.auth.Role;
import com.ticketrush.event.Event;
import com.ticketrush.event.EventRepository;
import com.ticketrush.event.EventStatus;
import com.ticketrush.seat.*;
import com.ticketrush.seat.dto.SeatMapResponse;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import com.ticketrush.venue.Venue;
import com.ticketrush.venue.VenueRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.outbox.publisher-enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
class SeatMapQueryCountTest {

    @Autowired ShowService showService;
    @Autowired UserRepository userRepository;
    @Autowired VenueRepository venueRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ShowRepository showRepository;
    @Autowired SeatTierRepository seatTierRepository;
    @Autowired SeatRepository seatRepository;

    @PersistenceContext EntityManager entityManager;

    @Test
    void getSeatMapFor500SeatsExecutesBoundedQueriesWithoutTierNPlusOne() {
        User organizer = new User();
        organizer.setName("Organizer");
        organizer.setEmail("nplus1-" + UUID.randomUUID() + "@test.local");
        organizer.setPasswordHash("hash");
        organizer.setRole(Role.ORGANIZER);
        organizer.setCreatedAt(OffsetDateTime.now());
        organizer.setUpdatedAt(OffsetDateTime.now());
        organizer.setActive(true);
        organizer = userRepository.save(organizer);

        Venue venue = venueRepository.save(new Venue("Arena", "NYC", "{}", OffsetDateTime.now()));

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setTitle("Stadium Tour");
        event.setDescription("500 seats");
        event.setCategory("Concert");
        event.setStatus(EventStatus.PUBLISHED);
        event.setCreatedAt(OffsetDateTime.now());
        event = eventRepository.save(event);

        Show show = new Show();
        show.setEvent(event);
        show.setVenue(venue);
        show.setStartsAt(OffsetDateTime.now().plusDays(1));
        show.setSaleOpensAt(OffsetDateTime.now().minusHours(1));
        show.setSaleClosesAt(OffsetDateTime.now().plusHours(12));
        show.setStatus(ShowStatus.SCHEDULED);
        show = showRepository.save(show);

        List<SeatTier> tiers = new ArrayList<>();
        for (int t = 1; t <= 20; t++) {
            SeatTier tier = new SeatTier();
            tier.setShow(show);
            tier.setName("Tier-" + t);
            tier.setPriceCents(1000L * t);
            tier.setCurrency("USD");
            tiers.add(seatTierRepository.save(tier));
        }

        List<Seat> seats = new ArrayList<>(500);
        for (int i = 0; i < 500; i++) {
            Seat seat = new Seat();
            seat.setShow(show);
            seat.setTier(tiers.get(i % tiers.size()));
            seat.setRowLabel("R" + (i / 25));
            seat.setSeatNumber((i % 25) + 1);
            seat.setStatus(SeatStatus.AVAILABLE);
            seats.add(seat);
        }
        seatRepository.saveAll(seats);

        entityManager.flush();
        entityManager.clear();

        SessionFactory sf = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        boolean prev = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();

        try {
            SeatMapResponse response = showService.getSeatMap(show.getId());
            assertThat(response.seats()).hasSize(500);
            assertThat(response.seats())
                    .allSatisfy(s -> {
                        assertThat(s.tierName()).startsWith("Tier-");
                        assertThat(s.priceCents()).isPositive();
                    });
            // 1 query for show lookup + 1 JOIN FETCH query for seats & tiers (independent of 20 tiers / 500 seats)
            assertThat(stats.getQueryExecutionCount()).isEqualTo(1L);
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4L);
        } finally {
            stats.setStatisticsEnabled(prev);
        }
    }
}
