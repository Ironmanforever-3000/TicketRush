package com.ticketrush.websocket;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.auth.Role;
import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.outbox.OutboxBatchService;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatStateChangedEvent;
import com.ticketrush.seat.SeatStatus;
import com.ticketrush.seat.SeatTier;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.venue.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "app.outbox.poll-delay-ms=60000",
        "app.redis.listener.enabled=true"
})
class SeatMapRealtimeEndToEndTest extends AbstractIntegrationTest {

    @SuppressWarnings("deprecation")
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @LocalServerPort
    int port;

    @Autowired
    OutboxBatchService batchService;

    public record Fixture(TestUser organizer, TestUser customer, Long showId, List<Long> seatIds) {}

    private Fixture setupShowWithAvailableSeats(int n) {
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
        for (int i = 1; i <= n; i++) {
            Seat seat = new Seat();
            seat.setShow(show);
            seat.setTier(tier);
            seat.setRowLabel("A");
            seat.setSeatNumber(i);
            seat.setStatus(SeatStatus.AVAILABLE);
            seats.add(seat);
        }
        seats = seatRepository.saveAll(seats);
        List<Long> seatIds = seats.stream().map(Seat::getId).toList();

        return new Fixture(organizer, customer, show.getId(), seatIds);
    }

    private void holdSeatsViaApi(Fixture f) throws Exception {
        HoldRequest req = new HoldRequest(f.customer().id(), f.seatIds());
        mvc.perform(post("/api/v1/shows/{showId}/holds", f.showId())
                .header("Authorization", bearer(f.customer().token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void holdingASeatReachesASubscribedBrowserClient() throws Exception {
        Fixture f = setupShowWithAvailableSeats(2);

        var received = new CompletableFuture<SeatStateChangedEvent>();
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter((JsonMapper) objectMapper));

        var connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + f.customer().token());

        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                        System.err.println("STOMP EXCEPTION: " + exception.getMessage());
                        exception.printStackTrace();
                    }
                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        System.err.println("STOMP TRANSPORT ERROR: " + exception.getMessage());
                        exception.printStackTrace();
                    }
                }).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/shows/" + f.showId() + "/seats", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) { return SeatStateChangedEvent.class; }
            @Override
            public void handleFrame(StompHeaders h, Object payload) { received.complete((SeatStateChangedEvent) payload); }
        });

        Thread.sleep(500);

        holdSeatsViaApi(f);

        int published = batchService.publishBatch();
        assertThat(published).isGreaterThanOrEqualTo(1);

        SeatStateChangedEvent event = received.get(5, TimeUnit.SECONDS);
        assertThat(event.status()).isEqualTo("HELD");
        assertThat(event.seatIds()).containsExactlyInAnyOrderElementsOf(f.seatIds());
        session.disconnect();
    }
}
