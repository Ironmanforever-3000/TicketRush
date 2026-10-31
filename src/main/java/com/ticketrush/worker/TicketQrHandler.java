package com.ticketrush.worker;

import com.ticketrush.ticket.QrCodeGenerator;
import com.ticketrush.ticket.Ticket;
import com.ticketrush.ticket.TicketCodeService;
import com.ticketrush.ticket.TicketRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class TicketQrHandler implements StreamEventHandler {
    private static final String GROUP = "ticket-generation";
    private final ProcessedMessageRepository processed;
    private final TicketRepository tickets;
    private final TicketCodeService codes;
    private final QrCodeGenerator qr;
    private final ObjectMapper mapper;

    public TicketQrHandler(ProcessedMessageRepository processed,
                           TicketRepository tickets,
                           TicketCodeService codes,
                           QrCodeGenerator qr,
                           ObjectMapper mapper) {
        this.processed = processed;
        this.tickets = tickets;
        this.codes = codes;
        this.qr = qr;
        this.mapper = mapper;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("BOOKING_CONFIRMED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(StreamEvent event) throws Exception {
        long eventId = Long.parseLong(event.eventId());
        if (!processed.tryInsert(GROUP, eventId)) {
            return;
        }

        long bookingId = mapper.readTree(event.payload()).get("booking_id").asLong();
        List<Ticket> bookingTickets = tickets.findByBookingId(bookingId);
        if (bookingTickets.isEmpty()) {
            throw new IllegalStateException("No tickets for booking " + bookingId + " — check issueForBooking wiring");
        }

        for (Ticket t : bookingTickets) {
            if (t.getQrPng() != null) {
                continue;
            }
            tickets.saveQrPng(t.getId(), qr.png(codes.encode(t.getShowId(), t.getId()), 320));
        }
    }
}
