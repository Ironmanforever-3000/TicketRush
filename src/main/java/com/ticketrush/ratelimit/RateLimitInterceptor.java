package com.ticketrush.ratelimit;

import com.ticketrush.exception.ErrorResponse;
import com.ticketrush.exception.ErrorResponse.ErrorDetail;
import com.ticketrush.exception.TraceIdContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Profile("!worker")
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final Set<String> FAIL_CLOSED_POLICIES = Set.of("holds", "bookings", "login");

    private final RateLimitService rateLimitService;
    private final Map<String, RateLimitPolicy> rateLimitPolicies;
    private final List<RouteRateLimitRule> routeRateLimitRules;
    private final ClientIpResolver ipResolver;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Counter redisErrorCounter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitInterceptor(
            RateLimitService rateLimitService,
            Map<String, RateLimitPolicy> rateLimitPolicies,
            List<RouteRateLimitRule> routeRateLimitRules,
            ClientIpResolver ipResolver,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.rateLimitPolicies = rateLimitPolicies;
        this.routeRateLimitRules = routeRateLimitRules;
        this.ipResolver = ipResolver;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.redisErrorCounter = Counter.builder("ticketrush.ratelimit.redis.errors")
                .description("Rate limiter Redis calls that failed")
                .register(meterRegistry);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        RouteRateLimitRule matchedRule = null;
        for (RouteRateLimitRule rule : routeRateLimitRules) {
            if (rule.method().name().equalsIgnoreCase(method) && pathMatcher.match(rule.pathPattern(), uri)) {
                matchedRule = rule;
                break;
            }
        }

        if (matchedRule == null) {
            return true;
        }

        RateLimitPolicy policy = rateLimitPolicies.get(matchedRule.policyName());
        if (policy == null) {
            return true;
        }

        String identity = resolveIdentity(request, policy.identityStrategy());
        String key = "ticketrush:ratelimit:" + policy.name() + ":" + identity;

        RateLimitResult result;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            result = rateLimitService.consume(key, policy);
        } catch (Exception ex) {
            redisErrorCounter.increment();
            log.error("Rate limiter Redis call failed for policy {}: {}", policy.name(), ex.getMessage(), ex);

            if (shouldFailClosed(policy.name())) {
                writeErrorResponse(
                        response,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE",
                        "Rate limit service temporarily unavailable"
                );
                return false;
            }
            return true;
        } finally {
            sample.stop(meterRegistry.timer("ticketrush.ratelimit.execution.time", "policy", policy.name()));
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetAt()));

        if (!result.allowed()) {
            meterRegistry.counter("ticketrush.ratelimit.rejected", "policy", policy.name()).increment();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(Math.max(1, result.retryAfterSeconds())));
            writeErrorResponse(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED",
                    "Too many requests. Please slow down and try again."
            );
            return false;
        }

        meterRegistry.counter("ticketrush.ratelimit.allowed", "policy", policy.name()).increment();
        return true;
    }

    private String resolveIdentity(HttpServletRequest request, IdentityStrategy strategy) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);

        return switch (strategy) {
            case IP -> "ip:" + ipResolver.resolve(request);
            case USER -> isAuthenticated ? ("user:" + extractUserId(auth)) : ("ip:" + ipResolver.resolve(request));
            case USER_OR_IP -> isAuthenticated ? ("user:" + extractUserId(auth)) : ("ip:" + ipResolver.resolve(request));
        };
    }

    private String extractUserId(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }
        return auth.getName();
    }

    private boolean shouldFailClosed(String policyName) {
        return FAIL_CLOSED_POLICIES.contains(policyName);
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorDetail detail = new ErrorDetail(code, message, null, TraceIdContext.currentOrNew());
        ErrorResponse errorResponse = new ErrorResponse(detail);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        response.getWriter().flush();
    }
}
