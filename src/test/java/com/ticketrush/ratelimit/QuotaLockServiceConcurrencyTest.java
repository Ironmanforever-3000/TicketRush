package com.ticketrush.ratelimit;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.auth.Role;
import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatStatus;
import com.ticketrush.seat.SeatTier;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.venue.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class QuotaLockServiceConcurrencyTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    void concurrentHoldRequestsNeverExceedSixSeatsPerShow() throws Exception {
        TestUser organizer = createUser(Role.ORGANIZER);
        TestUser customer = createUser(Role.CUSTOMER);

        Venue venue = new Venue();
        venue.setName("Venue " + UUID.randomUUID());
        venue.setCity("Metropolis");
        venue.setLayoutJson("{}");
        venue.setCreatedAt(OffsetDateTime.now());
        venue = venueRepository.save(venue);

        var event = new com.ticketrush.event.Event();
        event.setTitle("Concert " + UUID.randomUUID());
        event.setDescription("Description");
        event.setCategory("Music");
        event.setStatus(com.ticketrush.event.EventStatus.PUBLISHED);
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
        for (int i = 1; i <= 20; i++) {
            Seat seat = new Seat();
            seat.setShow(show);
            seat.setTier(tier);
            seat.setRowLabel("A");
            seat.setSeatNumber(i);
            seat.setStatus(SeatStatus.AVAILABLE);
            seats.add(seat);
        }
        final List<Seat> savedSeats = seatRepository.saveAll(seats);

        Long showId = show.getId();

        // Seed 5 held seats for customer (starts at 5/6)
        List<Long> firstFiveSeatIds = savedSeats.subList(0, 5).stream().map(Seat::getId).toList();
        HoldRequest initialHold = new HoldRequest(customer.id(), firstFiveSeatIds);
        mvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                .header("Authorization", bearer(customer.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialHold)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());

        // 10 concurrent requests to hold 1 more seat (seats 6 to 15)
        var pool = Executors.newFixedThreadPool(10);
        var go = new CountDownLatch(1);
        var successes = new AtomicInteger();

        var futures = IntStream.range(5, 15).mapToObj(i -> {
            Long candidateSeatId = savedSeats.get(i).getId();
            return pool.submit(() -> {
                try {
                    go.await();
                    HoldRequest req = new HoldRequest(customer.id(), List.of(candidateSeatId));
                    int status = mvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                            .header("Authorization", bearer(customer.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (status == 201) {
                        successes.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        }).toList();

        go.countDown();
        for (var f : futures) {
            f.get();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(meterRegistry.get("ticketrush.booking.quota.rejections").counter().count()).isGreaterThanOrEqualTo(1.0);
    }
}
