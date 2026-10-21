package com.ticketrush.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, 
                                                   JwtAuthenticationConverter jwtConverter,
                                                   RestSecurityErrorHandler errors) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .authorizeHttpRequests(auth -> auth
                // public
                .requestMatchers("/api/v1/auth/**", "/health", "/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/**", "/api/v1/venues/**", "/api/v1/shows/**").permitAll()
                // admin-only
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // buying: must come BEFORE the generic POST rules
                .requestMatchers(HttpMethod.POST, "/api/v1/shows/*/holds").hasAnyRole("CUSTOMER", "ORGANIZER", "ADMIN")
                .requestMatchers("/api/v1/bookings/**").hasAnyRole("CUSTOMER", "ORGANIZER", "ADMIN")
                // catalogue writes
                .requestMatchers(HttpMethod.POST, "/api/v1/events", "/api/v1/venues", "/api/v1/shows").hasAnyRole("ORGANIZER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/events/*", "/api/v1/venues/*", "/api/v1/shows/*").hasAnyRole("ORGANIZER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/events/*", "/api/v1/venues/*", "/api/v1/shows/*").hasAnyRole("ORGANIZER", "ADMIN")
                // safety net
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                .authenticationEntryPoint(errors)
                .accessDeniedHandler(errors));

        return http.build();
    }
}
