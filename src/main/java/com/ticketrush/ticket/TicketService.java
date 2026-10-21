package com.ticketrush.ticket;

import com.ticketrush.booking.BookingRepository;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.seat.SeatRepository;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {
    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository tickets;
    private final SeatRepository seats;
    private final ShowRepository shows;
    private final BookingRepository bookings;
    private final TicketCodeService codes;
    private final QrCodeGenerator qr;
    private final Duration opensBeforeStart;
    private final Duration closesAfterStart;

    public TicketService(TicketRepository tickets,
                         SeatRepository seats,
                         ShowRepository shows,
                         BookingRepository bookings,
                         TicketCodeService codes,
                         QrCodeGenerator qr,
                         @Value("${app.checkin.opens-before-start:PT3H}") Duration opensBeforeStart,
                         @Value("${app.checkin.closes-after-start:PT12H}") Duration closesAfterStart) {
        this.tickets = tickets;
        this.seats = seats;
        this.shows = shows;
        this.bookings = bookings;
        this.codes = codes;
        this.qr = qr;
        this.opensBeforeStart = opensBeforeStart;
        this.closesAfterStart = closesAfterStart;
    }

    /** MANDATORY: can only run inside the booking-confirmation transaction, so a confirmed booking always has tickets. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Ticket> issueForBooking(Long bookingId, Long showId, List<Long> seatIds) {
        return tickets.saveAll(seatIds.stream().map(seatId -> {
            Ticket t = new Ticket();
            t.setBookingId(bookingId);
            t.setShowId(showId);
            t.setSeatId(seatId);
            return t;
        }).toList());
    }

    @Transactional
    public CheckInResponse checkIn(Long showId, String rawCode, Long staffId) {
        var parsed = codes.verify(rawCode);                                   // 1. forged / garbage: no DB hit
        if (parsed.isEmpty()) return reject(CheckInResult.INVALID, "Code not recognised", showId, staffId, null);
        UUID ticketId = parsed.get().ticketId();
        if (!parsed.get().showId().equals(showId))
            return reject(CheckInResult.WRONG_SHOW, "Ticket is for a different show", showId, staffId, ticketId);

        Show show = shows.findById(showId).orElseThrow(() -> new ResourceNotFoundException("Show not found"));
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(show.getStartsAt().minus(opensBeforeStart)) || now.isAfter(show.getStartsAt().plus(closesAfterStart)))
            return reject(CheckInResult.OUTSIDE_WINDOW, "Check-in is not open for this show right now", showId, staffId, ticketId);

        int updated = tickets.markUsed(ticketId, showId, staffId);            // 2. the ONLY thing that decides admission
        Ticket ticket = tickets.findById(ticketId).orElse(null);              // 3. read only to explain the outcome
        if (ticket == null) return reject(CheckInResult.INVALID, "Ticket not found", showId, staffId, ticketId);

        if (updated == 1) {
            log.info("checkin ADMIT show={} staff={} ticket={}", showId, staffId, ticketId);
            var info = seatInfo(ticket.getSeatId());
            return new CheckInResponse(CheckInResult.ADMIT, "Admit", info[0], info[1], ticket.getCheckedInAt());
        }
        return switch (ticket.getStatus()) {
            case USED -> new CheckInResponse(CheckInResult.ALREADY_USED, "Ticket already used", seatInfo(ticket.getSeatId())[0], null, ticket.getCheckedInAt());
            case CANCELLED -> reject(CheckInResult.CANCELLED, "Ticket was cancelled", showId, staffId, ticketId);
            case VALID -> reject(CheckInResult.INVALID, "Ticket could not be checked in", showId, staffId, ticketId);
        };
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listForOwner(Long bookingId, Long userId) {
        if (!bookings.existsByIdAndUser_Id(bookingId, userId)) throw new ResourceNotFoundException("Booking not found"); // 404, not 403
        return tickets.findByBookingId(bookingId).stream().map(t -> {
            var info = seatInfo(t.getSeatId());
            return new TicketResponse(t.getId(), info[0], info[1], t.getStatus(), codes.encode(t.getShowId(), t.getId()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public byte[] qrForOwner(UUID ticketId, Long userId) {
        Ticket t = tickets.findByIdAndOwner(ticketId, userId).orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return qr.png(codes.encode(t.getShowId(), t.getId()), 320);
    }

    private String[] seatInfo(Long seatId) {
        return seats.findById(seatId)
                .map(s -> new String[]{ s.getRowLabel() + "-" + s.getSeatNumber(), s.getTier() != null ? s.getTier().getName() : null })
                .orElse(new String[]{"?", null});
    }

    private CheckInResponse reject(CheckInResult r, String msg, Long showId, Long staffId, UUID ticketId) {
        log.warn("checkin {} show={} staff={} ticket={}", r, showId, staffId, ticketId);   // never log the raw code: it is the credential
        return new CheckInResponse(r, msg, null, null, null);
    }
}
