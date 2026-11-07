package com.ticketrush.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${app.ratelimit.trusted-proxies:}") List<String> trustedProxies) {
        this.trustedProxies = new HashSet<>(trustedProxies != null ? trustedProxies : List.of());
    }

    public String resolve(HttpServletRequest request) {
        String directRemote = request.getRemoteAddr();
        if (directRemote == null) {
            return "unknown";
        }
        if (!trustedProxies.contains(directRemote)) {
            return directRemote;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return directRemote;
        }
        return forwarded.split(",")[0].trim();
    }
}
