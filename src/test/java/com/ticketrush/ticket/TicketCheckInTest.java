package com.ticketrush.ticket;

import tools.jackson.core.type.TypeReference;
import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.auth.Role;
import com.ticketrush.booking.BookingRequest;
import com.ticketrush.event.Event;
import com.ticketrush.event.EventStatus;
import com.ticketrush.hold.Hold;
import com.ticketrush.hold.HoldStatus;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatStatus;
import com.ticketrush.seat.SeatTier;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.venue.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TicketCheckInTest extends AbstractIntegrationTest {

    record Fixture(TestUser organizer, TestUser customer, Long showId, Long bookingId, List<String> codes, List<UUID> ticketIds) {}

    @Autowired TicketRepository ticketRepository;

    Fixture bookedShow(int n) throws Exception {
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
        List<Long> seatIds = seats.stream().map(s -> s.getId()).toList();

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
                .contentType(APPLICATION_JSON)
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
        List<String> codes = ticketResponses.stream().map(t -> t.code()).toList();
        List<UUID> ticketIds = ticketResponses.stream().map(t -> t.ticketId()).toList();

        return new Fixture(organizer, customer, show.getId(), bookingId, codes, ticketIds);
    }

    ResultActions scan(TestUser who, Long showId, String code) throws Exception {
        return mvc.perform(post("/api/v1/shows/{id}/checkin", showId)
                .header("Authorization", bearer(who.token())).contentType(APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)));
    }

    @Test
    void fiftyConcurrentScansAdmitExactlyOne() throws Exception {
        Fixture f = bookedShow(1);
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return scan(f.organizer(), f.showId(), f.codes().get(0)).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (var fu : futures) {
            statuses.add(fu.get());
        }
        pool.shutdown();
        assertThat(statuses.stream().filter(s -> s == 200)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(49);
    }

    @Test
    void forgedAndCrossShowCodesAreRejected() throws Exception {
        Fixture f = bookedShow(1), other = bookedShow(1);
        String code = f.codes().get(0);
        scan(f.organizer(), f.showId(), code.substring(0, code.length() - 2) + "AA").andExpect(status().is(422));
        scan(f.organizer(), f.showId(), other.codes().get(0)).andExpect(status().isConflict());     // WRONG_SHOW
        scan(f.organizer(), f.showId(), code).andExpect(status().isOk());                            // real one still works
    }

    @Test
    void cancelledTicketIsDenied() throws Exception {
        Fixture f = bookedShow(1);
        ticketRepository.cancelValidByBooking(f.bookingId());
        scan(f.organizer(), f.showId(), f.codes().get(0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.result").value("CANCELLED"));
    }

    @Test
    void onlyTheShowsOwnerOrAdminMayScan() throws Exception {
        Fixture f = bookedShow(1);
        scan(createUser(Role.ORGANIZER), f.showId(), f.codes().get(0)).andExpect(status().isForbidden());  // organizer B at A's show
        scan(f.customer(), f.showId(), f.codes().get(0)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shows/{id}/checkin", f.showId()).contentType(APPLICATION_JSON).content("{\"code\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        scan(createUser(Role.ADMIN), f.showId(), f.codes().get(0)).andExpect(status().isOk());
    }

    @Test
    void ticketsBelongToTheirBuyer() throws Exception {
        Fixture f = bookedShow(2);
        mvc.perform(get("/api/v1/bookings/{id}/tickets", f.bookingId()).header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/bookings/{id}/tickets", f.bookingId()).header("Authorization", bearer(createUser(Role.CUSTOMER).token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void qrImageCanBeFetchedByOwner() throws Exception {
        Fixture f = bookedShow(1);
        UUID ticketId = f.ticketIds().get(0);
        mvc.perform(get("/api/v1/tickets/{id}/qr.png", ticketId).header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray().length).isGreaterThan(100));

        mvc.perform(get("/api/v1/tickets/{id}/qr.png", ticketId).header("Authorization", bearer(createUser(Role.CUSTOMER).token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void failedBookingLeavesNoTickets() throws Exception {
        long before = ticketRepository.count();
        TestUser customer = createUser(Role.CUSTOMER);
        /* attempt a booking with non-existent hold / expired hold -> 404 */
        UUID idempotencyKey = UUID.randomUUID();
        String bookingJson = objectMapper.writeValueAsString(new BookingRequest(999999L, List.of(999999L)));
        mvc.perform(post("/api/v1/bookings")
                .header("Authorization", bearer(customer.token()))
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType(APPLICATION_JSON)
                .content(bookingJson))
                .andExpect(status().isNotFound());

        assertThat(ticketRepository.count()).isEqualTo(before);   // rolled back together
    }
}
