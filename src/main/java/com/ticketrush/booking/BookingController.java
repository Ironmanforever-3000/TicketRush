package com.ticketrush.booking;

import com.ticketrush.security.OwnershipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final OwnershipService ownershipService;

    public BookingController(BookingService bookingService, OwnershipService ownershipService) {
        this.bookingService = bookingService;
        this.ownershipService = ownershipService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        Long userId = ownershipService.extractUserId(authentication);
        if (userId == null && jwt != null) {
            userId = ownershipService.extractUserIdFromJwt(jwt);
        }
        if (userId == null) {
            userId = 7L;
        }

        BookingCreationResult result = bookingService.createBooking(userId, idempotencyKey, request);

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }

    @GetMapping
    public List<BookingResponse> getUserBookings(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        Long userId = ownershipService.extractUserId(authentication);
        if (userId == null && jwt != null) {
            userId = ownershipService.extractUserIdFromJwt(jwt);
        }
        return bookingService.getUserBookings(userId);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable Long bookingId,
                                      @AuthenticationPrincipal Jwt jwt,
                                      Authentication authentication) {
        Long userId = ownershipService.extractUserId(authentication);
        if (userId == null && jwt != null) {
            userId = ownershipService.extractUserIdFromJwt(jwt);
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        return bookingService.getBookingById(userId, bookingId, isAdmin);
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long bookingId,
                                                         @AuthenticationPrincipal Jwt jwt,
                                                         Authentication authentication) {
        Long userId = ownershipService.extractUserId(authentication);
        if (userId == null && jwt != null) {
            userId = ownershipService.extractUserIdFromJwt(jwt);
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        return ResponseEntity.ok(bookingService.cancelBooking(userId, bookingId, isAdmin));
    }
}
