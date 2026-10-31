package com.ticketrush.worker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.ticketrush.auth.Role;
import com.ticketrush.booking.BookingCreationResult;
import com.ticketrush.booking.BookingRequest;
import com.ticketrush.booking.BookingService;
import com.ticketrush.event.Event;
import com.ticketrush.event.EventRepository;
import com.ticketrush.event.EventStatus;
import com.ticketrush.hold.Hold;
import com.ticketrush.hold.HoldRepository;
import com.ticketrush.hold.HoldStatus;
import com.ticketrush.outbox.OutboxEvent;
import com.ticketrush.outbox.OutboxRepository;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatRepository;
import com.ticketrush.seat.SeatStatus;
import com.ticketrush.seat.SeatTier;
import com.ticketrush.seat.SeatTierRepository;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import com.ticketrush.venue.Venue;
import com.ticketrush.venue.VenueRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "app.worker.autostart=false"
})
public abstract class AbstractWorkerIntegrationTest {

    @SuppressWarnings("deprecation")
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        postgres.start();
        redisContainer.start();
    }

    @Autowired protected StringRedisTemplate redis;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected VenueRepository venueRepository;
    @Autowired protected EventRepository eventRepository;
    @Autowired protected ShowRepository showRepository;
    @Autowired protected SeatTierRepository seatTierRepository;
    @Autowired protected SeatRepository seatRepository;
    @Autowired protected HoldRepository holdRepository;
    @Autowired protected BookingService bookingService;
    @Autowired protected OutboxRepository outboxRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Value("${app.events.stream}") protected String stream;

    public record WorkerFixture(Long organizerId, Long customerId, Long showId, Long bookingId) {
        public Long organizerId() { return organizerId; }
        public Long customerId() { return customerId; }
        public Long showId() { return showId; }
        public Long bookingId() { return bookingId; }
    }

    protected User createUser(Role role) {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("TestPass123!"));
        user.setRole(role);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setActive(true);
        return userRepository.save(user);
    }

    protected WorkerFixture bookedShow(int n) {
        User organizer = createUser(Role.ORGANIZER);
        User customer = createUser(Role.CUSTOMER);

        Venue venue = new Venue();
        venue.setName("Venue " + UUID.randomUUID());
        venue.setCity("Metropolis");
        venue.setLayoutJson("{}");
        venue.setCreatedAt(OffsetDateTime.now());
        venue = venueRepository.save(venue);

        Event event = new Event();
        event.setTitle("Concert " + UUID.randomUUID());
        event.setDescription("Description");
        event.setCategory("Music");
        event.setStatus(EventStatus.PUBLISHED);
        event.setOrganizer(organizer);
        event.setCreatedAt(OffsetDateTime.now());
        event = eventRepository.save(event);

        Show show = new Show();
        show.setEvent(event);
        show.setVenue(venue);
        show.setStartsAt(OffsetDateTime.now().plusHours(1));
        show.setSaleOpensAt(OffsetDateTime.now().minusHours(1));
        show.setSaleClosesAt(OffsetDateTime.now().plusHours(3));
        show.setStatus(ShowStatus.SCHEDULED);
        show = showRepository.save(show);

        SeatTier tier = new SeatTier();
        tier.setShow(show);
        tier.setName("VIP");
        tier.setPriceCents(5000L);
        tier.setCurrency("USD");
        tier = seatTierRepository.save(tier);

        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Seat seat = new Seat();
            seat.setShow(show);
            seat.setTier(tier);
            seat.setRowLabel("A");
            seat.setSeatNumber(i);
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldBy(customer.getId());
            seat.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(10));
            seats.add(seat);
        }
        seats = seatRepository.saveAll(seats);
        List<Long> seatIds = seats.stream().map(Seat::getId).toList();

        Hold hold = new Hold();
        hold.setShow(show);
        hold.setUser(customer);
        hold.setSeatIds(seatIds);
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setCreatedAt(OffsetDateTime.now());
        hold.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        hold = holdRepository.save(hold);

        BookingCreationResult res = bookingService.createBooking(
                customer.getId(), UUID.randomUUID(), new BookingRequest(show.getId(), seatIds));

        return new WorkerFixture(organizer.getId(), customer.getId(), show.getId(), res.response().bookingId());
    }

    protected OutboxEvent latestOutboxRowFor(Long bookingId) {
        return outboxRepository.findAll().stream()
                .filter(o -> "BOOKING".equals(o.getAggregateType()) && bookingId.equals(o.getAggregateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Outbox event not found for booking " + bookingId));
    }

    protected StreamEvent confirmedEventFor(WorkerFixture f) {
        OutboxEvent outbox = outboxRepository.findAll().stream()
                .filter(o -> "BOOKING".equals(o.getAggregateType()) && f.bookingId().equals(o.getAggregateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Outbox event not found for booking " + f.bookingId()));
        return new StreamEvent(outbox.getId().toString(), outbox.getEventType(), outbox.getPayload(), outbox.getCreatedAt().toInstant());
    }

    protected void publishRaw(String eventId, String eventType, String payloadJson) {
        redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(Map.of(
                "eventId", eventId, "eventType", eventType,
                "payload", payloadJson, "occurredAt", Instant.now().toString())));
    }
}
