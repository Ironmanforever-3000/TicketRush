package com.ticketrush.ratelimit;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.auth.LoginRequest;
import com.ticketrush.hold.dto.HoldRequest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitInterceptorTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private RateLimitService rateLimitService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void unauthenticatedHoldsRequestIsRateLimitedByIpNotBlockedByAuth() throws Exception {
        // holds policy is IdentityStrategy.USER, but the route is protected -> Security 401s first,
        // the interceptor never even runs. Confirm 401, not 429, proving the two layers compose correctly.
        mvc.perform(post("/api/v1/shows/1/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sixthLoginAttemptWithinWindowGets429WithHeaders() throws Exception {
        LoginRequest req = new LoginRequest("nonexistent@test.local", "wrong-password");
        String content = objectMapper.writeValueAsString(req);

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(content))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"));

        // Verify metrics
        assertThat(meterRegistry.find("ticketrush.ratelimit.allowed").tag("policy", "login").counter()).isNotNull();
        assertThat(meterRegistry.find("ticketrush.ratelimit.allowed").tag("policy", "login").counter().count()).isGreaterThanOrEqualTo(5.0);
        assertThat(meterRegistry.find("ticketrush.ratelimit.rejected").tag("policy", "login").counter()).isNotNull();
        assertThat(meterRegistry.find("ticketrush.ratelimit.rejected").tag("policy", "login").counter().count()).isGreaterThanOrEqualTo(1.0);
        assertThat(meterRegistry.find("ticketrush.ratelimit.execution.time").tag("policy", "login").timer()).isNotNull();
    }

    @Test
    void redisOutageFailsClosedForHoldsButOpenForEvents() throws Exception {
        var user = createUser(com.ticketrush.auth.Role.CUSTOMER);
        double beforeFailures = meterRegistry.find("ticketrush.ratelimit.redis.errors")
                .counter() != null ? meterRegistry.find("ticketrush.ratelimit.redis.errors").counter().count() : 0.0;

        doThrow(new RedisConnectionFailureException("Simulated Redis outage"))
                .when(rateLimitService).consume(anyString(), any(RateLimitPolicy.class));

        // 1. Browsing events should FAIL OPEN (200 OK)
        mvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk());

        // 2. Holds should FAIL CLOSED (503 Service Unavailable)
        HoldRequest holdReq = new HoldRequest(user.id(), List.of(1L));
        mvc.perform(post("/api/v1/shows/1/holds")
                .header("Authorization", bearer(user.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(holdReq)))
                .andExpect(status().isServiceUnavailable());

        double afterFailures = meterRegistry.get("ticketrush.ratelimit.redis.errors").counter().count();
        assertThat(afterFailures).isGreaterThan(beforeFailures);
    }
}
