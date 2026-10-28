package com.oaiss.chain.security;

import com.oaiss.chain.config.SecurityConfig;
import com.oaiss.chain.config.SecurityStartupValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Security Classes - Coverage Enhancement")
class SecurityCoverageTest {

    private JwtUserDetails createUser() {
        return new JwtUserDetails(
                1L, "admin", "hashedpw", 4,
                List.of("ADMIN"), 1L, true, true, true, true
        );
    }

    @Test
    void jwtUserDetails_exposeFields() {
        JwtUserDetails d = createUser();
        assertEquals(1L, d.getUserId());
        assertEquals("admin", d.getUsername());
        assertEquals(Integer.valueOf(4), d.getUserType());
        assertEquals(1L, d.getEnterpriseId());
    }

    @Test
    void jwtUserDetails_roles() {
        JwtUserDetails d = createUser();
        Collection<? extends GrantedAuthority> auths = d.getAuthorities();
        assertNotNull(auths);
        assertEquals(1, auths.size());
        assertTrue(auths.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void jwtUserDetails_accountAndEnabled() {
        JwtUserDetails d = createUser();
        assertTrue(d.isEnabled());
        assertTrue(d.hasRole("ADMIN"));
        assertTrue(d.isAdmin());
    }

    @Test
    void jwtUserDetails_password() {
        JwtUserDetails d = createUser();
        assertEquals("hashedpw", d.getPassword());
    }

    @Test
    void securityConfig_annotations() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class));
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity.class));
    }

    @Test
    @DisplayName("SecurityConfig 声明了 corsConfigurationSource 与 securityFilterChain")
    void securityConfig_declaresRequiredBeans() throws NoSuchMethodException {
        assertNotNull(SecurityConfig.class.getMethod("corsConfigurationSource"));
        assertNotNull(SecurityConfig.class.getMethod("securityFilterChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class));
    }

    @Test
    void securityStartupValidator_component() {
        assertTrue(SecurityStartupValidator.class.isAnnotationPresent(
                org.springframework.stereotype.Component.class));
    }
}