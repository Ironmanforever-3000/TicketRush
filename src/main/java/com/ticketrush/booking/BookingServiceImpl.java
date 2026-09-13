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
import com.ticketrush.seat.SeatUnavailableException;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final OutboxRepository outboxRepository;
    private final SeatRepository seatRepository;
    private final HoldRepository holdRepository;
    private final ShowRepository showRepository;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            OutboxRepository outboxRepository,
            SeatRepository seatRepository,
            HoldRepository holdRepository,
            ShowRepository showRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.outboxRepository = outboxRepository;
        this.seatRepository = seatRepository;
        this.holdRepository = holdRepository;
        this.showRepository = showRepository;
    }

    @Override
    @Transactional
    public BookingCreationResult createBooking(Long userId, UUID idempotencyKey, BookingRequest request) {

        List<Long> seatIds = request.seatIds().stream()
                .distinct()
                .sorted()
                .toList();

        if (seatIds.isEmpty()) {
            throw new IllegalArgumentException("At least one seat is required");
        }

        // 1. Check idempotency reuse directly (optimization)
        Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
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
            Booking duplicate = bookingRepository.findByIdempotencyKey(idempotencyKey)
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

        // 7. Update booking -> CONFIRMED
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.getBookingSeats().addAll(bookingSeats); // For response generation
        bookingRepository.save(booking);

        // 8. Write outbox event
        String payloadJson = String.format("{\"booking_id\":%d, \"show_id\":%d, \"seat_ids\":%s, \"user_id\":%d}",
                booking.getId(), show.getId(), seatIds.toString(), userId);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("BOOKING");
        event.setAggregateId(booking.getId());
        event.setEventType("BOOKING_CONFIRMED");
        event.setPayload(payloadJson);
        event.setCreatedAt(OffsetDateTime.now());

        outboxRepository.save(event);

        return new BookingCreationResult(toResponse(booking), true);
    }

    private Hold findValidHold(Long userId, Long showId, List<Long> seatIds) {
        List<Hold> validHolds = holdRepository.findByUserIdAndShowIdAndStatusAndExpiresAtAfter(
                userId, showId, HoldStatus.ACTIVE, OffsetDateTime.now());
        
        for (Hold hold : validHolds) {
            if (new HashSet<>(hold.getSeatIds()).containsAll(seatIds)) {
                return hold;
            }
        }
        throw new SeatUnavailableException("Valid hold not found for the requested seats");
    }

    private void validateIdempotencyReuse(Booking booking, Long userId, Long showId, List<Long> seatIds) {
        if (!booking.getUser().getId().equals(userId) || !booking.getShow().getId().equals(showId)) {
            throw new IdempotencyKeyReusedException("The idempotency key was already used for a different booking.");
        }
        // Extract seats and compare
        List<Long> existingSeats = booking.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getId())
                .sorted()
                .toList();
        
        if (!existingSeats.equals(seatIds)) {
            throw new IdempotencyKeyReusedException("The idempotency key was already used for a different booking.");
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
}
