package com.ticketrush.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody BookingRequest request
            // In a real app we inject Authentication here, but for test/phase 2 we'll simulate user 7
    ) {
        // Temporarily hardcoded for Phase 2 implementation as directed (simulating current user)
        Long userId = 7L; 

        BookingCreationResult result = bookingService.createBooking(userId, idempotencyKey, request);

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }
}
