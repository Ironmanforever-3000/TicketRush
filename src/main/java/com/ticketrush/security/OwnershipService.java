package com.ticketrush.security;

import com.ticketrush.show.ShowRepository;
import com.ticketrush.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("ownership")
public class OwnershipService {

    private final ShowRepository showRepository;
    private final UserRepository userRepository;

    public OwnershipService(ShowRepository showRepository, UserRepository userRepository) {
        this.showRepository = showRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean isShowOwner(Long showId, Authentication authentication) {
        if (showId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Admin has universal access
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"))) {
            return true;
        }

        Long userId = extractUserId(authentication);
        if (userId == null) {
            return false;
        }

        return showRepository.existsByIdAndEvent_Organizer_Id(showId, userId);
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
