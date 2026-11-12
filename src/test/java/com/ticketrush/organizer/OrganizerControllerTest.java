package com.ticketrush.organizer;

import com.ticketrush.auth.AuthService;
import com.ticketrush.auth.InvalidCredentialsException;
import com.ticketrush.auth.Role;
import com.ticketrush.booking.*;
import com.ticketrush.event.Event;
import com.ticketrush.event.EventRepository;
import com.ticketrush.event.EventStatus;
import com.ticketrush.event.dto.EventCreateRequest;
import com.ticketrush.event.dto.EventUpdateRequest;
import com.ticketrush.exception.BusinessRuleException;
import com.ticketrush.hold.Hold;
import com.ticketrush.hold.HoldRepository;
import com.ticketrush.hold.HoldService;
import com.ticketrush.hold.HoldStatus;
import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.seat.*;
import com.ticketrush.security.JwtService;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.show.dto.SeatTierRequest;
import com.ticketrush.show.dto.ShowCreateRequest;
import com.ticketrush.show.dto.ShowUpdateRequest;
import com.ticketrush.ticket.Ticket;
import com.ticketrush.ticket.TicketRepository;
import com.ticketrush.ticket.TicketService;
import com.ticketrush.ticket.TicketStatus;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import com.ticketrush.venue.Venue;
import com.ticketrush.venue.VenueRepository;
import com.ticketrush.venue.dto.VenueUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrganizerControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired VenueRepository venueRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ShowRepository showRepository;
    @Autowired SeatTierRepository seatTierRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired HoldRepository holdRepository;
    @Autowired BookingService bookingService;
    @Autowired HoldService holdService;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TicketService ticketService;
    @Autowired TicketRepository ticketRepository;
    @Autowired RefundRepository refundRepository;

    @Test
    void statsOwnershipAdminAndBookingCancelWorkInH2() throws Exception {
        User org1 = saveUser(Role.ORGANIZER);
        User org2 = saveUser(Role.ORGANIZER);
        User customer = saveUser(Role.CUSTOMER);
        User admin = saveUser(Role.ADMIN);

        Venue venue = venueRepository.save(new Venue("Hall", "NYC", "{}", OffsetDateTime.now()));
        Event event = new Event();
        event.setOrganizer(org1);
        event.setTitle("Show Title");
        event.setDescription("Desc");
        event.setCategory("Music");
        event.setStatus(EventStatus.DRAFT);
        event.setCreatedAt(OffsetDateTime.now());
        event = eventRepository.save(event);

        Show show = new Show();
        show.setEvent(event);
        show.setVenue(venue);
        show.setStartsAt(OffsetDateTime.now().plusDays(1));
        show.setSaleOpensAt(OffsetDateTime.now().minusHours(1));
        show.setSaleClosesAt(OffsetDateTime.now().plusHours(5));
        show.setStatus(ShowStatus.SCHEDULED);
        show = showRepository.save(show);

        SeatTier tier = new SeatTier();
        tier.setShow(show);
        tier.setName("VIP");
        tier.setPriceCents(5000L);
        tier.setCurrency("USD");
        tier = seatTierRepository.save(tier);

        Seat s1 = new Seat();
        s1.setShow(show);
        s1.setTier(tier);
        s1.setRowLabel("A");
        s1.setSeatNumber(1);
        s1.setStatus(SeatStatus.AVAILABLE);
        s1 = seatRepository.save(s1);

        String org1Token = "Bearer " + jwtService.generateToken(org1.getEmail(), org1.getRole().name(), org1.getId());
        String org2Token = "Bearer " + jwtService.generateToken(org2.getEmail(), org2.getRole().name(), org2.getId());
        String custToken = "Bearer " + jwtService.generateToken(customer.getEmail(), customer.getRole().name(), customer.getId());
        String adminToken = "Bearer " + jwtService.generateToken(admin.getEmail(), admin.getRole().name(), admin.getId());

        mvc.perform(patch("/api/v1/events/{id}", event.getId())
                        .header("Authorization", org1Token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventUpdateRequest("Updated", null, null, EventStatus.PUBLISHED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/v1/organizer/shows/{id}/stats", show.getId())
                        .header("Authorization", org2Token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/organizer/shows/{id}/stats", show.getId())
                        .header("Authorization", custToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/organizer/shows/{id}/stats", show.getId())
                        .header("Authorization", org1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(1));

        mvc.perform(get("/api/v1/admin/health")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("UP"));

        mvc.perform(post("/api/v1/admin/events/{id}/suspend", event.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        final Long showId = show.getId();
        final Long s1Id = s1.getId();
        assertThatThrownBy(() -> holdService.createHold(showId, new HoldRequest(customer.getId(), List.of(s1Id))))
                .isInstanceOf(BusinessRuleException.class);

        mvc.perform(patch("/api/v1/shows/{id}", show.getId())
                        .header("Authorization", org2Token)
                        .header("X-Trace-Id", "trace-local-check")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShowUpdateRequest(null, null, null, ShowStatus.CANCELLED))))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Trace-Id", "trace-local-check"))
                .andExpect(jsonPath("$.error.traceId").value("trace-local-check"));

        mvc.perform(patch("/api/v1/venues/{id}", venue.getId())
                        .header("Authorization", custToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VenueUpdateRequest("Hacked", null, null))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/events")
                        .header("Authorization", org2Token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EventCreateRequest(org1.getId(), "Impersonated", "Desc", "Music"))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/shows")
                        .header("Authorization", org2Token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShowCreateRequest(
                                event.getId(), venue.getId(),
                                OffsetDateTime.now().plusDays(2), OffsetDateTime.now().plusHours(1),
                                OffsetDateTime.now().plusDays(1), ShowStatus.SCHEDULED,
                                List.of(new SeatTierRequest("VIP", 1000L, "USD"))))))
                .andExpect(status().isForbidden());

        // Non-transactional booking create -> cancel -> refund -> seat release verification
        Hold hold = new Hold();
        hold.setShow(show);
        hold.setUser(customer);
        hold.setSeatIds(List.of(s1.getId()));
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setCreatedAt(OffsetDateTime.now());
        hold.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        holdRepository.save(hold);

        s1.setStatus(SeatStatus.HELD);
        s1.setHeldBy(customer.getId());
        s1.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(10));
        seatRepository.save(s1);

        var created = bookingService.createBooking(customer.getId(), UUID.randomUUID(),
                new BookingRequest(show.getId(), List.of(s1.getId())));
        Long bookingId = created.response().bookingId();

        mvc.perform(get("/api/v1/organizer/shows/{id}/stats", show.getId())
                        .header("Authorization", org1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldSeats").value(1))
                .andExpect(jsonPath("$.confirmedBookings").value(1))
                .andExpect(jsonPath("$.confirmedRevenueCents").value(5000))
                .andExpect(jsonPath("$.organizerTotalRevenueCents").value(5000));

        mvc.perform(post("/api/v1/bookings/{bookingId}/cancel", bookingId)
                        .header("Authorization", custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(delete("/api/v1/bookings/{bookingId}", bookingId)
                        .header("Authorization", custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/v1/bookings/{bookingId}/refund", bookingId)
                        .header("Authorization", custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(5000))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        Seat released = seatRepository.findById(s1.getId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(released.getHeldBy()).isNull();

        // Booking with a USED ticket rejects cancellation with 409 and creates no refund
        Seat s2 = new Seat();
        s2.setShow(show);
        s2.setTier(tier);
        s2.setRowLabel("A");
        s2.setSeatNumber(2);
        s2.setStatus(SeatStatus.HELD);
        s2.setHeldBy(customer.getId());
        s2.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(10));
        s2 = seatRepository.save(s2);

        Hold usedHold = new Hold();
        usedHold.setShow(show);
        usedHold.setUser(customer);
        usedHold.setSeatIds(List.of(s2.getId()));
        usedHold.setStatus(HoldStatus.ACTIVE);
        usedHold.setCreatedAt(OffsetDateTime.now());
        usedHold.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        holdRepository.save(usedHold);

        Long usedBookingId = bookingService.createBooking(customer.getId(), UUID.randomUUID(),
                new BookingRequest(show.getId(), List.of(s2.getId()))).response().bookingId();
        Ticket usedTicket = ticketRepository.findByBookingId(usedBookingId).get(0);
        usedTicket.setStatus(TicketStatus.USED);
        ticketRepository.save(usedTicket);

        mvc.perform(post("/api/v1/bookings/{bookingId}/cancel", usedBookingId)
                        .header("Authorization", custToken)
                        .header("X-Trace-Id", "used-ticket-trace"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", "used-ticket-trace"))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.traceId").value("used-ticket-trace"));

        assertThat(refundRepository.findByBookingId(usedBookingId)).isEmpty();

        // Admin suspend user blocks auth
        mvc.perform(post("/api/v1/admin/users/{id}/suspend", customer.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private User saveUser(Role role) {
        User u = new User();
        u.setName(role.name());
        u.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setCreatedAt(OffsetDateTime.now());
        u.setUpdatedAt(OffsetDateTime.now());
        u.setActive(true);
        return userRepository.save(u);
    }
}
