package com.ticketrush.auth;

import com.ticketrush.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator tokenGenerator;
    private final long refreshTokenValidityDays = 7;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenGenerator tokenGenerator) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenGenerator = tokenGenerator;
    }

    @Transactional
    public String createToken(User user) {
        String plainToken = tokenGenerator.generateOpaqueToken();
        String tokenHash = tokenGenerator.hashToken(plainToken);
        UUID familyId = UUID.randomUUID();

        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHash,
                familyId,
                OffsetDateTime.now().plusDays(refreshTokenValidityDays),
                OffsetDateTime.now()
        );

        refreshTokenRepository.save(refreshToken);
        return plainToken;
    }

    @Transactional
    public Optional<User> validateToken(String plainToken) {
        String tokenHash = tokenGenerator.hashToken(plainToken);
        
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findForUpdateByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            return Optional.empty(); // Not found
        }
        
        RefreshToken token = tokenOpt.get();
        
        // Check for reuse attack
        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), OffsetDateTime.now());
            return Optional.empty(); // Compromised family
        }
        
        // Check expiration
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return Optional.empty(); // Expired
        }
        
        // Mark as used
        token.setUsedAt(OffsetDateTime.now());
        
        return Optional.of(token.getUser());
    }

    @Transactional
    public String rotateToken(String oldPlainToken) {
        String oldTokenHash = tokenGenerator.hashToken(oldPlainToken);
        Optional<RefreshToken> oldTokenOpt = refreshTokenRepository.findByTokenHash(oldTokenHash);
        
        if (oldTokenOpt.isEmpty()) {
            throw new IllegalArgumentException("Token not found");
        }
        
        RefreshToken oldToken = oldTokenOpt.get();
        
        // Generate new token in same family
        String newPlainToken = tokenGenerator.generateOpaqueToken();
        String newTokenHash = tokenGenerator.hashToken(newPlainToken);
        
        RefreshToken newToken = new RefreshToken(
                oldToken.getUser(),
                newTokenHash,
                oldToken.getFamilyId(), // Keep same family
                OffsetDateTime.now().plusDays(refreshTokenValidityDays),
                OffsetDateTime.now()
        );
        
        refreshTokenRepository.save(newToken);
        return newPlainToken;
    }

    @Transactional
    public void revokeToken(String plainToken) {
        String tokenHash = tokenGenerator.hashToken(plainToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), OffsetDateTime.now());
        });
    }
}
