package com.ticketrush.admin;

import com.ticketrush.admin.dto.AdminHealthResponse;
import com.ticketrush.admin.dto.AdminUserSuspensionResponse;
import com.ticketrush.auth.RefreshTokenRepository;
import com.ticketrush.event.Event;
import com.ticketrush.event.EventRepository;
import com.ticketrush.event.EventStatus;
import com.ticketrush.event.dto.EventResponse;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.hold.HoldRepository;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class AdminService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final HoldRepository holdRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public AdminService(
            EventRepository eventRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            HoldRepository holdRepository,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.holdRepository = holdRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Transactional
    public EventResponse suspendEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        event.setStatus(EventStatus.SUSPENDED);
        event = eventRepository.save(event);
        return new EventResponse(
                event.getId(),
                event.getOrganizer().getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }

    @Transactional
    public AdminUserSuspensionResponse suspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setActive(false);
        user = userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId, OffsetDateTime.now());
        return new AdminUserSuspensionResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive()
        );
    }

    @Transactional(readOnly = true)
    public AdminHealthResponse checkHealth() {
        String dbStatus = checkDatabase();
        String redisStatus = checkRedis();
        long activeHolds = "UP".equals(dbStatus) ? holdRepository.countAllActive(OffsetDateTime.now()) : 0L;
        String overall = ("UP".equals(dbStatus) && !"DOWN".equals(redisStatus)) ? "UP" : "DEGRADED";
        return new AdminHealthResponse(overall, dbStatus, redisStatus, activeHolds, OffsetDateTime.now());
    }

    private String checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return (result != null && result == 1) ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }

    private String checkRedis() {
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null || template.getConnectionFactory() == null) {
            return "UNCONFIGURED";
        }
        try {
            String pong = template.execute((RedisConnection conn) -> conn.ping());
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}
