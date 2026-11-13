package com.ticketrush.security;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.auth.LoginRequest;
import com.ticketrush.auth.Role;
import com.ticketrush.event.EventStatus;
import com.ticketrush.event.dto.EventCreateRequest;
import com.ticketrush.event.dto.EventUpdateRequest;
import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatStatus;
import com.ticketrush.seat.SeatTier;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.show.dto.SeatTierRequest;
import com.ticketrush.show.dto.ShowCreateRequest;
import com.ticketrush.show.dto.ShowUpdateRequest;
import com.ticketrush.user.User;
import com.ticketrush.venue.dto.VenueUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SecurityHardeningAndOwnershipTest extends AbstractIntegrationTest {

    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void customerBlockedOnPatchAndCrossOrganizerBlockedWithOwnershipEnforced() throws Exception {
        Fixture f = bookedShow(2);
        Show show = showRepository.findById(f.showId()).orElseThrow();
        Long eventId = show.getEvent().getId();
        Long venueId = show.getVenue().getId();
        TestUser otherOrganizer = createUser(Role.ORGANIZER);

        // 1. CUSTOMER cannot PATCH events, shows, venues, or read organizer stats
        mvc.perform(patch("/api/v1/events/{id}", eventId)
                        .header("Authorization", bearer(f.customer().token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventUpdateRequest("Hacked", null, null, null))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/shows/{id}", f.showId())
                        .header("Authorization", bearer(f.customer().token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShowUpdateRequest(null, null, null, ShowStatus.CANCELLED))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/venues/{id}", venueId)
                        .header("Authorization", bearer(f.customer().token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VenueUpdateRequest("Hacked", null, null))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/organizer/shows/{id}/stats", f.showId())
                        .header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isForbidden());

        // 2. Different ORGANIZER cannot mutate another organizer's event/show or read their stats
        mvc.perform(post("/api/v1/events")
                        .header("Authorization", bearer(otherOrganizer.token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EventCreateRequest(f.organizer().id(), "Impersonated", "Desc", "Music"))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/events/{id}", eventId)
                        .header("Authorization", bearer(otherOrganizer.token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventUpdateRequest("Stolen", null, null, null))))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/events/{id}", eventId)
                        .header("Authorization", bearer(otherOrganizer.token())))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/shows")
                        .header("Authorization", bearer(otherOrganizer.token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShowCreateRequest(
                                eventId, venueId,
                                OffsetDateTime.now().plusDays(2), OffsetDateTime.now().plusHours(1),
                                OffsetDateTime.now().plusDays(1), ShowStatus.SCHEDULED,
                                List.of(new SeatTierRequest("VIP", 1000L, "USD"))))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/shows/{id}", f.showId())
                        .header("Authorization", bearer(otherOrganizer.token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShowUpdateRequest(null, null, null, ShowStatus.CANCELLED))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/organizer/shows/{id}/stats", f.showId())
                        .header("Authorization", bearer(otherOrganizer.token())))
                .andExpect(status().isForbidden());

        // 3. Owning ORGANIZER can PATCH event status cleanly (verifying DRAFT bug fix) and read stats
        mvc.perform(patch("/api/v1/events/{id}", eventId)
                        .header("Authorization", bearer(f.organizer().token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EventUpdateRequest("Updated Concert", null, null, EventStatus.PUBLISHED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Concert"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/v1/organizer/shows/{id}/stats", f.showId())
                        .header("Authorization", bearer(f.organizer().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(f.showId()))
                .andExpect(jsonPath("$.soldSeats").value(2))
                .andExpect(jsonPath("$.confirmedBookings").value(1))
                .andExpect(jsonPath("$.confirmedRevenueCents").value(10000));
    }

    @Test
    void adminSuspendEventBlocksHoldsSuspendUserBlocksLoginAndHealthReportsUpWithTraceCorrelation() throws Exception {
        Fixture f = bookedShow(1);
        Show show = showRepository.findById(f.showId()).orElseThrow();
        Long eventId = show.getEvent().getId();

        SeatTier tier = seatRepository.findByShowIdOrderByRowLabelAscSeatNumberAsc(f.showId()).get(0).getTier();
        Seat availableSeat = new Seat();
        availableSeat.setShow(show);
        availableSeat.setTier(tier);
        availableSeat.setRowLabel("B");
        availableSeat.setSeatNumber(1);
        availableSeat.setStatus(SeatStatus.AVAILABLE);
        availableSeat = seatRepository.save(availableSeat);

        TestUser admin = createUser(Role.ADMIN);

        // Admin suspends event -> HOLD fails with 409 + X-Trace-Id matching body.traceId
        mvc.perform(post("/api/v1/admin/events/{id}/suspend", eventId)
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        MvcResult holdRejected = mvc.perform(post("/api/v1/shows/{showId}/holds", f.showId())
                        .header("Authorization", bearer(f.customer().token()))
                        .header("X-Trace-Id", "trace-suspend-check")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HoldRequest(f.customer().id(), List.of(availableSeat.getId())))))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", "trace-suspend-check"))
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.traceId").value("trace-suspend-check"))
                .andReturn();
        assertThat(holdRejected.getResponse().getHeader("X-Trace-Id")).isEqualTo("trace-suspend-check");

        // Admin suspends a user with real password -> login fails
        User target = new User();
        target.setName("Target User");
        target.setEmail("target-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
        target.setPasswordHash(passwordEncoder.encode("StrongPass123!"));
        target.setRole(Role.CUSTOMER);
        target.setCreatedAt(OffsetDateTime.now());
        target.setUpdatedAt(OffsetDateTime.now());
        target.setActive(true);
        target = userRepository.save(target);

        mvc.perform(post("/api/v1/admin/users/{id}/suspend", target.getId())
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(target.getEmail(), "StrongPass123!"))))
                .andExpect(status().isUnauthorized());

        // Admin health check
        mvc.perform(get("/api/v1/admin/health")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.redis").value("UP"));
    }
}
