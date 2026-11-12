package com.ticketrush.security;

import com.ticketrush.event.EventRepository;
import com.ticketrush.show.ShowRepository;
import com.ticketrush.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.LongPredicate;

@Service("ownership")
public class OwnershipService {

    private final EventRepository eventRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;

    public OwnershipService(
            EventRepository eventRepository,
            ShowRepository showRepository,
            UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
    }

    @Transactional(readOnly = true)
    public boolean isShowOwner(Long showId, Authentication authentication) {
        return showId != null && checkOwnership(authentication, userId ->
                showRepository.existsByIdAndEvent_Organizer_Id(showId, userId));
    }

    @Transactional(readOnly = true)
    public boolean isEventOwner(Long eventId, Authentication authentication) {
        return eventId != null && checkOwnership(authentication, userId ->
                eventRepository.existsByIdAndOrganizer_Id(eventId, userId));
    }

    @Transactional(readOnly = true)
    public boolean isSelf(Long targetUserId, Authentication authentication) {
        return targetUserId != null && checkOwnership(authentication, userId ->
                targetUserId.equals(userId));
    }

    private boolean checkOwnership(Authentication authentication, LongPredicate predicate) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        Long userId = extractUserId(authentication);
        return userId != null && predicate.test(userId);
    }

    public Long requireUserId(Jwt jwt, Authentication authentication) {
        Long userId = extractUserId(authentication);
        if (userId == null && jwt != null) {
            userId = extractUserIdFromJwt(jwt);
        }
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        return userId;
    }

    public Long extractUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return extractUserIdFromJwt(jwtAuth.getToken());
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return extractUserIdFromJwt(jwt);
        }
        var userOpt1 = userRepository.findByEmail(authentication.getName());
        return userOpt1.isPresent() ? userOpt1.get().getId() : null;
    }

    public Long extractUserIdFromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object userIdClaim = jwt.getClaim("userId");
        if (userIdClaim instanceof Number n) {
            return n.longValue();
        } else if (userIdClaim != null) {
            try {
                return Long.parseLong(userIdClaim.toString());
            } catch (NumberFormatException ignored) {}
        }
        String sub = jwt.getSubject();
        if (sub != null) {
            try {
                return Long.parseLong(sub);
            } catch (NumberFormatException ignored) {}
            var userOpt2 = userRepository.findByEmail(sub);
            return userOpt2.isPresent() ? userOpt2.get().getId() : null;
        }
        return null;
    }
}
