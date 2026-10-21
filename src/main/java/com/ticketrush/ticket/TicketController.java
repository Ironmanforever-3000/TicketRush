package com.ticketrush.ticket;

import com.ticketrush.security.OwnershipService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TicketController {

    private final TicketService ticketService;
    private final OwnershipService ownershipService;

    public TicketController(TicketService ticketService, OwnershipService ownershipService) {
        this.ticketService = ticketService;
        this.ownershipService = ownershipService;
    }

    @GetMapping("/api/v1/bookings/{bookingId}/tickets")
    public List<TicketResponse> list(@PathVariable Long bookingId,
                                     @AuthenticationPrincipal Jwt jwt,
                                     Authentication authentication) {
        Long userId = ownershipService.extractUserId(authentication);
        if (userId == null && jwt != null) {
            userId = ownershipService.extractUserIdFromJwt(jwt);
        }
        return ticketService.listForOwner(bookingId, userId);
    }

    @GetMapping(value = "/api/v1/tickets/{ticketId}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr(@PathVariable UUID ticketId,
                     @AuthenticationPrincipal Jwt jwt,
                     Authentication authentication) {
        Long userId = ownershipService.extractUserId(authentication);
        if (userId == null && jwt != null) {
            userId = ownershipService.extractUserIdFromJwt(jwt);
        }
        return ticketService.qrForOwner(ticketId, userId);
    }
}
