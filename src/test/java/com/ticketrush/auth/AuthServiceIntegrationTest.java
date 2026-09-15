package com.ticketrush.auth;

import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("Alice", "ALICE@example.com", "StrongPassword123!");
        
        RegisterResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo("CUSTOMER");

        User savedUser = userRepository.findById(response.userId()).orElseThrow();
        assertThat(savedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
        
        // Assert password was encoded
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("StrongPassword123!");
        assertThat(passwordEncoder.matches("StrongPassword123!", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request1 = new RegisterRequest("Bob", "bob@example.com", "StrongPassword123!");
        authService.register(request1);

        RegisterRequest request2 = new RegisterRequest("Bob Two", "BOB@example.com", "AnotherPassword123!");
        
        assertThatThrownBy(() -> authService.register(request2))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("Email is already registered");
    }

    // Role cannot be passed in via RegisterRequest (Privilege escalation test)
    // The DTO doesn't have a role, so even if Jackson bound it, it wouldn't exist.
    // However, if we test the service, we can prove it always overrides any malicious intent.
    @Test
    void register_AlwaysAssignsCustomerRole() {
        RegisterRequest request = new RegisterRequest("Attacker", "attacker@example.com", "StrongPassword123!");
        
        RegisterResponse response = authService.register(request);
        
        assertThat(response.role()).isEqualTo("CUSTOMER");
        
        User savedUser = userRepository.findById(response.userId()).orElseThrow();
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void login_Success() {
        authService.register(new RegisterRequest("Charlie", "charlie@example.com", "Secret123!"));

        LoginResponse response = authService.login(new LoginRequest("CHARLIE@example.com", "Secret123!"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void login_InvalidPassword_ThrowsException() {
        authService.register(new RegisterRequest("Dave", "dave@example.com", "Secret123!"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("dave@example.com", "WrongPassword!")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_UserNotFound_ThrowsException() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "Secret123!")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
