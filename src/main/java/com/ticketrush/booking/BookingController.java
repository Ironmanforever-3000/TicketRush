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
        Long userId = requireUserId(jwt, authentication);
        BookingCreationResult result = bookingService.createBooking(userId, idempotencyKey, request);

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }

    @GetMapping
    public List<BookingResponse> getUserBookings(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return bookingService.getUserBookings(requireUserId(jwt, authentication));
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable Long bookingId,
                                      @AuthenticationPrincipal Jwt jwt,
                                      Authentication authentication) {
        Long userId = requireUserId(jwt, authentication);
        return bookingService.getBookingById(userId, bookingId, ownershipService.isAdmin(authentication));
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long bookingId,
                                                         @AuthenticationPrincipal Jwt jwt,
                                                         Authentication authentication) {
        return performCancel(bookingId, jwt, authentication);
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBookingViaPost(@PathVariable Long bookingId,
                                                                @AuthenticationPrincipal Jwt jwt,
                                                                Authentication authentication) {
        return performCancel(bookingId, jwt, authentication);
    }

    @GetMapping("/{bookingId}/refund")
    public RefundResponse getRefund(@PathVariable Long bookingId,
                                    @AuthenticationPrincipal Jwt jwt,
                                    Authentication authentication) {
        Long userId = requireUserId(jwt, authentication);
        return bookingService.getRefundByBookingId(userId, bookingId, ownershipService.isAdmin(authentication));
    }

    private ResponseEntity<BookingResponse> performCancel(Long bookingId, Jwt jwt, Authentication authentication) {
        Long userId = requireUserId(jwt, authentication);
        return ResponseEntity.ok(bookingService.cancelBooking(userId, bookingId, ownershipService.isAdmin(authentication)));
    }

    private Long requireUserId(Jwt jwt, Authentication authentication) {
        return ownershipService.requireUserId(jwt, authentication);
    }
}
