package com.ticketrush.waitingroom;

import com.ticketrush.exception.BadRequestException;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.security.OwnershipService;
import com.ticketrush.show.ShowRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shows/{showId}/waiting-room")
public class WaitingRoomController {

    private final WaitingRoomService waitingRoomService;
    private final ShowRepository showRepository;
    private final OwnershipService ownershipService;

    public WaitingRoomController(
            WaitingRoomService waitingRoomService,
            ShowRepository showRepository,
            OwnershipService ownershipService) {
        this.waitingRoomService = waitingRoomService;
        this.showRepository = showRepository;
        this.ownershipService = ownershipService;
    }

    @PostMapping("/join")
    public JoinQueueResponse joinQueue(
            @PathVariable Long showId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        ensureShowExists(showId);
        Long userId = requireUserId(jwt, authentication);
        long queueNumber = waitingRoomService.joinQueue(showId, userId);
        return new JoinQueueResponse(showId, userId, queueNumber);
    }

    @GetMapping("/position")
    public QueuePositionResponse getPosition(
            @PathVariable Long showId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        ensureShowExists(showId);
        Long userId = requireUserId(jwt, authentication);
        Long position = waitingRoomService.getQueuePosition(showId, userId);
        return new QueuePositionResponse(showId, userId, position != null, position);
    }

    @PostMapping("/admit")
    @PreAuthorize("hasRole('ADMIN') or @ownership.isShowOwner(#showId, authentication)")
    public List<WaitingRoomService.AdmittedUser> admitNextBatch(
            @PathVariable Long showId,
            @RequestParam(defaultValue = "50") int count) {
        if (count < 1 || count > 500) {
            throw new BadRequestException("count must be between 1 and 500");
        }
        ensureShowExists(showId);
        return waitingRoomService.admitNextBatch(showId, count);
    }

    @PostMapping("/validate")
    public ValidateTokenResponse validateToken(
            @PathVariable Long showId,
            @Valid @RequestBody ValidateTokenRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        ensureShowExists(showId);
        Long userId = requireUserId(jwt, authentication);
        boolean valid = waitingRoomService.validateToken(request.token(), showId, userId);
        return new ValidateTokenResponse(showId, userId, valid);
    }

    private void ensureShowExists(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show not found with id: " + showId);
        }
    }

    private Long requireUserId(Jwt jwt, Authentication authentication) {
        return ownershipService.requireUserId(jwt, authentication);
    }

    public record ValidateTokenRequest(@NotBlank String token) {}
    public record JoinQueueResponse(Long showId, Long userId, long queueNumber) {}
    public record QueuePositionResponse(Long showId, Long userId, boolean inQueue, Long position) {}
    public record ValidateTokenResponse(Long showId, Long userId, boolean valid) {}
}
