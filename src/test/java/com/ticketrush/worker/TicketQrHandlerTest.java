package com.ticketrush.worker;

import com.ticketrush.ticket.Ticket;
import com.ticketrush.ticket.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketQrHandlerTest extends AbstractWorkerIntegrationTest {
    @Autowired TicketQrHandler handler;
    @Autowired TicketRepository tickets;

    @Test
    void generatesOneQrPerBookedSeat() throws Exception {
        WorkerFixture f = bookedShow(3);
        handler.handle(confirmedEventFor(f));
        List<Ticket> ts = tickets.findByBookingId(f.bookingId());
        assertThat(ts).hasSize(3).allSatisfy(t -> assertThat(t.getQrPng()).isNotEmpty());
    }

    @Test
    void redeliveryDoesNotDuplicateOrRewrite() throws Exception {
        WorkerFixture f = bookedShow(2);
        var event = confirmedEventFor(f);
        handler.handle(event);
        byte[] first = tickets.findByBookingId(f.bookingId()).get(0).getQrPng();
        handler.handle(event);
        List<Ticket> after = tickets.findByBookingId(f.bookingId());
        assertThat(after).hasSize(2);
        assertThat(after.get(0).getQrPng()).isEqualTo(first);
    }

    @Test
    void missingTicketRowsThrowsSoTheMessageStaysPendingForRetry() {
        var fake = new StreamEvent("999999", "BOOKING_CONFIRMED", "{\"booking_id\":999999}", Instant.now());
        assertThatThrownBy(() -> handler.handle(fake)).isInstanceOf(IllegalStateException.class);
    }
}
