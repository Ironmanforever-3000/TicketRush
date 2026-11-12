package com.ticketrush.organizer;

import com.ticketrush.auth.Role;
import com.ticketrush.booking.*;
import com.ticketrush.event.Event;
import com.ticketrush.event.EventRepository;
import com.ticketrush.event.EventStatus;
import com.ticketrush.event.dto.EventUpdateRequest;
import com.ticketrush.hold.Hold;
import com.ticketrush.hold.HoldRepository;
import com.ticketrush.hold.HoldStatus;
import com.ticketrush.seat.*;
import com.ticketrush.security.JwtService;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.ticket.TicketRepository;
import com.ticketrush.ticket.TicketService;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import com.ticketrush.venue.Venue;
import com.ticketrush.venue.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired TicketService ticketService;
    @Autowired TicketRepository ticketRepository;

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

        mvc.perform(post("/api/v1/bookings/{bookingId}/cancel", bookingId)
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
