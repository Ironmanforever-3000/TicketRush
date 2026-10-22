package com.ticketrush;

import com.ticketrush.auth.Role;
import com.ticketrush.booking.BookingRepository;
import com.ticketrush.booking.BookingRequest;
import com.ticketrush.booking.BookingSeatRepository;
import com.ticketrush.event.Event;
import com.ticketrush.event.EventRepository;
import com.ticketrush.event.EventStatus;
import com.ticketrush.hold.Hold;
import com.ticketrush.hold.HoldRepository;
import com.ticketrush.hold.HoldStatus;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatRepository;
import com.ticketrush.seat.SeatStatus;
import com.ticketrush.seat.SeatTier;
import com.ticketrush.seat.SeatTierRepository;
import com.ticketrush.security.JwtService;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.ticket.TicketResponse;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import com.ticketrush.venue.Venue;
import com.ticketrush.venue.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;

import java.time.OffsetDateTime;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired protected JwtService jwtService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected VenueRepository venueRepository;
    @Autowired protected EventRepository eventRepository;
    @Autowired protected ShowRepository showRepository;
    @Autowired protected SeatTierRepository seatTierRepository;
    @Autowired protected SeatRepository seatRepository;
    @Autowired protected HoldRepository holdRepository;
    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected BookingSeatRepository bookingSeatRepository;
    @Autowired protected JdbcTemplate jdbc;

    public record TestUser(Long id, String email, String token, Role role) {}
    public record Fixture(TestUser organizer, TestUser customer, Long showId, Long bookingId, List<String> codes, List<UUID> ticketIds) {}

    @BeforeEach
    public void initAsyncTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS processed_events (
                consumer_group VARCHAR(64) NOT NULL,
                event_id       VARCHAR(64) NOT NULL,
                processed_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                PRIMARY KEY (consumer_group, event_id)
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ticket_qr (
                ticket_id  UUID PRIMARY KEY REFERENCES tickets(id),
                png        BYTEA NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS show_sales (
                show_id       BIGINT PRIMARY KEY REFERENCES shows(id),
                tickets_sold  BIGINT NOT NULL DEFAULT 0,
                revenue_cents BIGINT NOT NULL DEFAULT 0,
                updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
            )
        """);
    }

    public TestUser createUser(Role role) {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        User user = new User();
        user.setName("Test " + role.name());
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setActive(true);
        user = userRepository.save(user);

        String token = jwtService.generateToken(email, role.name(), user.getId());
        return new TestUser(user.getId(), email, token, role);
    }

    public String bearer(String token) {
        return "Bearer " + token;
    }

    public Fixture bookedShow(int n) throws Exception {
        TestUser organizer = createUser(Role.ORGANIZER);
        TestUser customer = createUser(Role.CUSTOMER);

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
        event.setOrganizer(userRepository.findById(organizer.id()).orElseThrow());
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
            seat.setHeldBy(customer.id());
            seat.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(10));
            seats.add(seat);
        }
        seats = seatRepository.saveAll(seats);
        List<Long> seatIds = seats.stream().map(Seat::getId).toList();

        Hold hold = new Hold();
        hold.setShow(show);
        hold.setUser(userRepository.findById(customer.id()).orElseThrow());
        hold.setSeatIds(seatIds);
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setCreatedAt(OffsetDateTime.now());
        hold.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        hold = holdRepository.save(hold);

        UUID idempotencyKey = UUID.randomUUID();
        String bookingJson = objectMapper.writeValueAsString(new BookingRequest(show.getId(), seatIds));

        String bookingRes = mvc.perform(post("/api/v1/bookings")
                .header("Authorization", bearer(customer.token()))
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var bookingTree = objectMapper.readTree(bookingRes);
        Long bookingId = bookingTree.has("bookingId") ? bookingTree.get("bookingId").asLong() : bookingTree.get("id").asLong();

        String ticketsJson = mvc.perform(get("/api/v1/bookings/{id}/tickets", bookingId)
                .header("Authorization", bearer(customer.token())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<TicketResponse> ticketResponses = objectMapper.readValue(ticketsJson, new TypeReference<List<TicketResponse>>() {});
        List<String> codes = ticketResponses.stream().map(TicketResponse::code).toList();
        List<UUID> ticketIds = ticketResponses.stream().map(TicketResponse::ticketId).toList();

        return new Fixture(organizer, customer, show.getId(), bookingId, codes, ticketIds);
    }
}
