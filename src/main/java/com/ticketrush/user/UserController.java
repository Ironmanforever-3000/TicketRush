package com.ticketrush.user;

import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.user.dto.UserProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserProfileResponse getCurrentUser(Authentication authentication) {
        String email = null;
        Long userId = null;

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Object userIdClaim = jwtAuth.getToken().getClaim("userId");
            if (userIdClaim instanceof Number n) {
                userId = n.longValue();
            } else if (userIdClaim != null) {
                try {
                    userId = Long.parseLong(userIdClaim.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            email = jwtAuth.getToken().getClaimAsString("email");
            if (email == null) {
                email = jwtAuth.getToken().getSubject();
            }
        } else if (authentication != null) {
            email = authentication.getName();
        }

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null && email != null) {
            user = userRepository.findByEmail(email).orElse(null);
        }

        if (user == null) {
            throw new ResourceNotFoundException("Authenticated user profile not found");
        }

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
