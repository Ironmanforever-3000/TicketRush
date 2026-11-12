package com.ticketrush.booking;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatStatus;
import com.ticketrush.ticket.Ticket;
import com.ticketrush.ticket.TicketRepository;
import com.ticketrush.ticket.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class BookingCancellationRefundTest extends AbstractIntegrationTest {

    @Autowired TicketRepository ticketRepository;
    @Autowired RefundRepository refundRepository;

    @Test
    void postCancelReleasesSeatsCancelsTicketsAndCreatesRefund() throws Exception {
        Fixture f = bookedShow(2);

        List<Seat> seatsBefore = seatRepository.findByShowIdOrderByRowLabelAscSeatNumberAsc(f.showId());
        assertThat(seatsBefore).allMatch(s -> s.getStatus() == SeatStatus.SOLD);

        mvc.perform(post("/api/v1/bookings/{bookingId}/cancel", f.bookingId())
                        .header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.totalCents").value(10000));

        // Seats released back to AVAILABLE and can be held again
        List<Seat> seatsAfter = seatRepository.findByShowIdOrderByRowLabelAscSeatNumberAsc(f.showId());
        assertThat(seatsAfter).allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE && s.getHeldBy() == null);

        List<Ticket> tickets = ticketRepository.findByBookingId(f.bookingId());
        assertThat(tickets).allMatch(t -> t.getStatus() == TicketStatus.CANCELLED);

        mvc.perform(get("/api/v1/bookings/{bookingId}/refund", f.bookingId())
                        .header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(f.bookingId()))
                .andExpect(jsonPath("$.amountCents").value(10000))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.reason").value("USER_CANCELLED"));

        // Released seat can immediately be held again
        mvc.perform(post("/api/v1/shows/{showId}/holds", f.showId())
                        .header("Authorization", bearer(f.customer().token()))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new HoldRequest(f.customer().id(), List.of(seatsAfter.get(0).getId())))))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteCancelIsIdempotentAndUsedTicketCannotBeCancelled() throws Exception {
        Fixture f = bookedShow(1);

        // DELETE /api/v1/bookings/{id} works identically and idempotently
        mvc.perform(delete("/api/v1/bookings/{bookingId}", f.bookingId())
                        .header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(post("/api/v1/bookings/{bookingId}/cancel", f.bookingId())
                        .header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(refundRepository.findByBookingId(f.bookingId())).isPresent();

        // Booking with a USED ticket rejects cancellation with 409 and creates no refund
        Fixture usedFixture = bookedShow(1);
        Ticket ticket = ticketRepository.findByBookingId(usedFixture.bookingId()).get(0);
        ticket.setStatus(TicketStatus.USED);
        ticketRepository.save(ticket);

        mvc.perform(post("/api/v1/bookings/{bookingId}/cancel", usedFixture.bookingId())
                        .header("Authorization", bearer(usedFixture.customer().token())))
                .andExpect(status().isConflict());

        assertThat(refundRepository.findByBookingId(usedFixture.bookingId())).isEmpty();
    }
}
