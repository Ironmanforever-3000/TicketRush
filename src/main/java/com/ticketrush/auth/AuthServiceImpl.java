package com.ticketrush.auth;

import com.ticketrush.security.JwtService;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.CUSTOMER);

        OffsetDateTime now = OffsetDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setActive(true);

        User saved = userRepository.save(user);

        return new RegisterResponse(
                saved.getId(),
                saved.getName(),
                saved.getEmail(),
                saved.getRole().name()
        );
    }

    @Override
    @Transactional // Changed to read-write for saving token
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        String refreshToken = refreshTokenService.createToken(user);

        return new LoginResponse(token, refreshToken, "Bearer", 900); // 15 mins = 900 seconds
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse refresh(RefreshRequest request) {
        User user = refreshTokenService.validateToken(request.refreshToken())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

        String newRefreshToken = refreshTokenService.rotateToken(request.refreshToken());
        String newAccessToken = jwtService.generateToken(user.getEmail(), user.getRole().name(), user.getId());

        return new LoginResponse(newAccessToken, newRefreshToken, "Bearer", 900);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        }
    }
}
