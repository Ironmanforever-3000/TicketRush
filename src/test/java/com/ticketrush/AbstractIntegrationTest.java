package com.ticketrush;

import com.ticketrush.auth.Role;
import com.ticketrush.booking.BookingRepository;
import com.ticketrush.booking.BookingSeatRepository;
import com.ticketrush.event.EventRepository;
import com.ticketrush.hold.HoldRepository;
import com.ticketrush.seat.SeatRepository;
import com.ticketrush.seat.SeatTierRepository;
import com.ticketrush.security.JwtService;
import com.ticketrush.show.ShowRepository;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import com.ticketrush.venue.VenueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired protected JwtService jwtService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected VenueRepository venueRepository;
    @Autowired protected EventRepository eventRepository;
    @Autowired protected ShowRepository showRepository;
    @Autowired protected SeatTierRepository seatTierRepository;
    @Autowired protected SeatRepository seatRepository;
    @Autowired protected HoldRepository holdRepository;
    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected BookingSeatRepository bookingSeatRepository;

    public record TestUser(Long id, String email, String token, Role role) {}

    public TestUser createUser(Role role) {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        User user = new User();
        user.setName("Test " + role.name());
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setActive(true);
        user = userRepository.save(user);

        String token = jwtService.generateToken(email, role.name(), user.getId());
        return new TestUser(user.getId(), email, token, role);
    }

    public String bearer(String token) {
        return "Bearer " + token;
    }
}
