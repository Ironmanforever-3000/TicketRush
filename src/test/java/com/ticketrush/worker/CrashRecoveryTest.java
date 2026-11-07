package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticketrush.ticket.TicketRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CrashRecoveryTest extends AbstractWorkerIntegrationTest {
    @Autowired TicketQrHandler ticketHandler;
    @Autowired PendingMessageReclaimer reclaimer;
    @Autowired ConsumerGroupInitializer init;
    @Autowired TicketRepository tickets;

    @Test
    @SuppressWarnings("unchecked")
    void crashAfterCommitBeforeAckDoesNotDuplicateTickets() throws Exception {
        init.ensureGroup("ticket-generation");
        var f = bookedShow(2);
        var outboxRow = latestOutboxRowFor(f.bookingId());          // helper reading the real outbox row
        publishRaw(outboxRow.getId().toString(), "BOOKING_CONFIRMED", outboxRow.getPayload());

        // Simulate a worker reading the message (creating a pending entry) and dying right
        // after its own commit but before XACK — run the handler for real, then deliberately
        // skip ack() and markDone(). That's exactly what a crashed process leaves behind.
        redis.<String, String>opsForStream().read(Consumer.from("ticket-generation", "crashed-worker"),
                StreamReadOptions.empty().count(1), StreamOffset.create(stream, ReadOffset.lastConsumed()));
        ticketHandler.handle(new StreamEvent(outboxRow.getId().toString(), "BOOKING_CONFIRMED", outboxRow.getPayload(), Instant.now()));

        assertThat(tickets.findByBookingId(f.bookingId())).hasSize(2);                       // committed once, correctly
        assertThat(redis.opsForStream().pending(stream, "ticket-generation").getTotalPendingMessages()).isEqualTo(1);

        ReflectionTestUtils.setField(reclaimer, "pendingIdleSeconds", 0L);                    // test-only: treat it as idle now
        reclaimer.reclaimAbandoned();                                                          // the "restart"

        assertThat(tickets.findByBookingId(f.bookingId())).hasSize(2);                        // still 2, not 4
        assertThat(redis.opsForStream().pending(stream, "ticket-generation").getTotalPendingMessages()).isZero();
    }
}
