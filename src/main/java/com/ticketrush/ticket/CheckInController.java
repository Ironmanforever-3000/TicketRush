package com.ticketrush.ticket;

import com.ticketrush.security.OwnershipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shows/{showId}/checkin")
public class CheckInController {

    private final TicketService ticketService;
    private final OwnershipService ownershipService;

    public CheckInController(TicketService ticketService, OwnershipService ownershipService) {
        this.ticketService = ticketService;
        this.ownershipService = ownershipService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @ownership.isShowOwner(#showId, authentication)")
    public ResponseEntity<CheckInResponse> checkIn(@PathVariable Long showId,
                                                   @Valid @RequestBody CheckInRequest request,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   Authentication authentication) {
        Long staffId = ownershipService.extractUserId(authentication);
        if (staffId == null && jwt != null) {
            staffId = ownershipService.extractUserIdFromJwt(jwt);
        }
        CheckInResponse r = ticketService.checkIn(showId, request.code(), staffId);
        HttpStatus status = switch (r.result()) {
            case ADMIT -> HttpStatus.OK;
            case INVALID -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(r);
    }
}
