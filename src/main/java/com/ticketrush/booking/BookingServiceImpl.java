package com.ticketrush.booking;

import com.ticketrush.exception.IdempotencyKeyReusedException;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.hold.Hold;
import com.ticketrush.hold.HoldRepository;
import com.ticketrush.hold.HoldStatus;
import com.ticketrush.outbox.OutboxEvent;
import com.ticketrush.outbox.OutboxRepository;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatRepository;
import com.ticketrush.seat.SeatStateOutboxWriter;
import com.ticketrush.seat.SeatUnavailableException;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import com.ticketrush.ticket.TicketRepository;
import com.ticketrush.ticket.TicketService;
import com.ticketrush.ticket.TicketStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class BookingServiceImpl implements BookingService {

    private static final ConcurrentMap<UUID, Object> IDEMPOTENCY_LOCKS = new ConcurrentHashMap<>();

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final OutboxRepository outboxRepository;
    private final SeatRepository seatRepository;
    private final HoldRepository holdRepository;
    private final ShowRepository showRepository;
    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final RefundRepository refundRepository;
    private final ObjectMapper objectMapper;
    private final SeatStateOutboxWriter seatStateOutboxWriter;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            OutboxRepository outboxRepository,
            SeatRepository seatRepository,
            HoldRepository holdRepository,
            ShowRepository showRepository,
            TicketService ticketService,
            TicketRepository ticketRepository,
            RefundRepository refundRepository,
            ObjectMapper objectMapper,
            SeatStateOutboxWriter seatStateOutboxWriter) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.outboxRepository = outboxRepository;
        this.seatRepository = seatRepository;
        this.holdRepository = holdRepository;
        this.showRepository = showRepository;
        this.ticketService = ticketService;
        this.ticketRepository = ticketRepository;
        this.refundRepository = refundRepository;
        this.objectMapper = objectMapper;
        this.seatStateOutboxWriter = seatStateOutboxWriter;
    }

    @Override
    @Transactional
    public BookingCreationResult createBooking(Long userId, UUID idempotencyKey, BookingRequest request) {
        Object lock = IDEMPOTENCY_LOCKS.computeIfAbsent(idempotencyKey, key -> new Object());

        synchronized (lock) {
            try {
                List<Long> seatIds = request.seatIds().stream()
                        .distinct()
                        .sorted()
                        .toList();

                // 1. Check idempotency reuse directly (optimization)
                Optional<Booking> existing = bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
                if (existing.isPresent()) {
                    Booking booking = existing.get();
                    validateIdempotencyReuse(booking, userId, request.showId(), seatIds);
                    return new BookingCreationResult(toResponse(booking), false);
                }

                // 2. Load Show and Hold
                Show show = showRepository.findById(request.showId())
                        .orElseThrow(() -> new ResourceNotFoundException("Show not found"));

                Hold hold = findValidHold(userId, request.showId(), seatIds);

                // 3. Calculate total
                List<Seat> seats = seatRepository.findAllById(seatIds);
                long totalCents = calculateTotal(seats);

                // 4. Insert booking atomically
                Optional<Long> inserted = bookingRepository.insertIfAbsent(
                        userId,
                        show.getId(),
                        hold.getId(),
                        totalCents,
                        idempotencyKey
                );

                if (inserted.isEmpty()) {
                    Booking duplicate = bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                            .orElseThrow(() -> new IllegalStateException("Booking should exist if insertIfAbsent returned empty"));
                    validateIdempotencyReuse(duplicate, userId, request.showId(), seatIds);
                    return new BookingCreationResult(toResponse(duplicate), false);
                }

                Long bookingId = inserted.get();

                // 5. Convert HELD -> SOLD
                int updated = seatRepository.confirmHeldSeats(seatIds, request.showId(), userId);
                if (updated != seatIds.size()) {
                    throw new SeatUnavailableException("One or more seats are no longer valid, or hold expired");
                }

                Booking booking = bookingRepository.findById(bookingId).orElseThrow();

                // 6. Create booking_seats rows
                List<BookingSeat> bookingSeats = new ArrayList<>();
                for (Seat seat : seats) {
                    BookingSeat bookingSeat = new BookingSeat();
                    bookingSeat.setId(new BookingSeatId(booking.getId(), seat.getId()));
                    bookingSeat.setBooking(booking);
                    bookingSeat.setSeat(seat);
                    bookingSeat.setPriceCents(seat.getTier().getPriceCents());
                    bookingSeats.add(bookingSeat);
                }
                bookingSeatRepository.saveAll(bookingSeats);
                ticketService.issueForBooking(booking.getId(), show.getId(), seatIds);

                // 7. Update booking -> CONFIRMED
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.getBookingSeats().addAll(bookingSeats); // For response generation
                bookingRepository.save(booking);

                hold.setStatus(HoldStatus.CONFIRMED);
                holdRepository.save(hold);

                // 8. Write outbox event
                String payloadJson;
                try {
                    payloadJson = objectMapper.writeValueAsString(Map.of(
                            "booking_id", booking.getId(),
                            "show_id", show.getId(),
                            "seat_ids", seatIds,
                            "user_id", userId,
                            "total_cents", booking.getTotalCents()));
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to serialize outbox event payload", ex);
                }

                OutboxEvent event = new OutboxEvent();
                event.setAggregateType("BOOKING");
                event.setAggregateId(booking.getId());
                event.setEventType("BOOKING_CONFIRMED");
                event.setPayload(payloadJson);
                event.setCreatedAt(OffsetDateTime.now());

                outboxRepository.save(event);
                seatStateOutboxWriter.write(request.showId(), seatIds, "SOLD");

                return new BookingCreationResult(toResponse(booking), true);
            } finally {
                IDEMPOTENCY_LOCKS.remove(idempotencyKey, lock);
            }
        }
    }

    private Hold findValidHold(Long userId, Long showId, List<Long> seatIds) {
        List<Hold> validHolds = holdRepository.findByUserIdAndShowIdAndStatusAndExpiresAtAfter(
                userId, showId, HoldStatus.ACTIVE, OffsetDateTime.now());
        
        for (Hold hold : validHolds) {
            if (new HashSet<>(hold.getSeatIds()).containsAll(seatIds)) {
                return hold;
            }
        }
        
        throw new SeatUnavailableException("No active hold found for the selected seats");
    }

    private void validateIdempotencyReuse(Booking booking, Long userId, Long showId, List<Long> seatIds) {
        boolean showMatches = booking.getShow().getId().equals(showId);
        boolean userMatches = booking.getUser().getId().equals(userId);
        
        List<Long> bookedSeatIds = booking.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getId())
                .sorted()
                .toList();
        boolean seatsMatch = bookedSeatIds.equals(seatIds);

        if (!showMatches || !userMatches || !seatsMatch) {
            throw new IdempotencyKeyReusedException("Idempotency key reused with different parameters");
        }
    }

    private long calculateTotal(List<Seat> seats) {
        return seats.stream().mapToLong(seat -> seat.getTier().getPriceCents()).sum();
    }

    private BookingResponse toResponse(Booking booking) {
        List<Long> seatIds = booking.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getId())
                .toList();
        
        return new BookingResponse(
                booking.getId(),
                booking.getShow().getId(),
                seatIds,
                booking.getTotalCents(),
                booking.getStatus().name()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long userId, Long bookingId, boolean isAdmin) {
        return toResponse(findAuthorizedBooking(userId, bookingId, isAdmin));
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long userId, Long bookingId, boolean isAdmin) {
        Booking booking = findAuthorizedBooking(userId, bookingId, isAdmin);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return toResponse(booking);
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only confirmed bookings can be cancelled");
        }
        boolean hasUsedTickets = ticketRepository.findByBookingId(bookingId).stream()
                .anyMatch(t -> t.getStatus() == TicketStatus.USED);
        if (hasUsedTickets) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot cancel booking with used tickets");
        }

        ticketRepository.cancelValidByBooking(bookingId);

        Long showId = booking.getShow().getId();
        List<Long> seatIds = booking.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getId())
                .sorted()
                .toList();

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        if (refundRepository.findByBookingId(bookingId).isEmpty()) {
            Refund refund = new Refund();
            refund.setBooking(booking);
            refund.setUser(booking.getUser());
            refund.setAmountCents(booking.getTotalCents());
            refund.setStatus(RefundStatus.PROCESSED);
            refund.setReason("USER_CANCELLED");
            refundRepository.save(refund);
        }

        if (!seatIds.isEmpty()) {
            seatRepository.releaseSoldSeats(seatIds, showId);
            seatStateOutboxWriter.write(showId, seatIds, "AVAILABLE");
        }

        return new BookingResponse(
                booking.getId(),
                showId,
                seatIds,
                booking.getTotalCents(),
                BookingStatus.CANCELLED.name()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponse getRefundByBookingId(Long userId, Long bookingId, boolean isAdmin) {
        findAuthorizedBooking(userId, bookingId, isAdmin);
        Refund refund = refundRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found"));
        return RefundResponse.from(refund);
    }

    private Booking findAuthorizedBooking(Long userId, Long bookingId, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!isAdmin && !booking.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Booking not found");
        }
        return booking;
    }
}
