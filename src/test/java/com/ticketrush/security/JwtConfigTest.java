package com.ticketrush.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigTest {
    @Test
    void roleClaimBecomesRoleAuthority() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("123").claim("role", "CUSTOMER").build();
        var auth = new SecurityBeansConfig().jwtAuthenticationConverter().convert(jwt);
        boolean hasCustomerRole = false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (ga != null && "ROLE_CUSTOMER".equals(ga.getAuthority())) {
                hasCustomerRole = true;
            }
        }
        assertThat(hasCustomerRole).isTrue();
    }
}
