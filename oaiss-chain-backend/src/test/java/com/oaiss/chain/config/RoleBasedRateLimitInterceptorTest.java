package com.oaiss.chain.config;

import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RoleBasedRateLimitInterceptor}.
 *
 * Verifies:
 *   - Correct rate limits per role (ADMIN=200, REVIEWER=80, ENTERPRISE=60, THIRD_PARTY=40)
 *   - Anonymous fallback limit (10/min)
 *   - Exceeded limit throws BusinessException
 *   - Redis failure gracefully allows the request
 *   - Response headers are set correctly
 *
 * @author OAISS Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleBasedRateLimitInterceptor tests")
class RoleBasedRateLimitInterceptorTest {

    @Mock
    private RedisTemplate<String, Long> redisScriptTemplate;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private RoleBasedRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // Clear security context before each test
        SecurityContextHolder.clearContext();
    }

    /**
     * Helper: set up a JwtUserDetails principal with given roles in the SecurityContext.
     */
    private void setSecurityContext(String... roles) {
        JwtUserDetails userDetails = JwtUserDetails.builder()
                .userId(1L)
                .username("testuser")
                .roles(List.of(roles))
                .enabled(true)
                .build();

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(userDetails);

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("ENTERPRISE user: allowed when under 60/min limit")
    void preHandle_enterpriseUserUnderLimit_shouldAllow() {
        setSecurityContext("ENTERPRISE");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(10L);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(response).setHeader("X-RateLimit-Role", "ENTERPRISE");
        verify(response).setHeader("X-RateLimit-Limit", "60");
        verify(response).setHeader("X-RateLimit-Remaining", "50");
    }

    @Test
    @DisplayName("ADMIN user: allowed when under 200/min limit")
    void preHandle_adminUserUnderLimit_shouldAllow() {
        setSecurityContext("ADMIN");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(50L);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(response).setHeader("X-RateLimit-Role", "ADMIN");
        verify(response).setHeader("X-RateLimit-Limit", "200");
    }

    @Test
    @DisplayName("REVIEWER user: allowed when under 80/min limit")
    void preHandle_reviewerUserUnderLimit_shouldAllow() {
        setSecurityContext("REVIEWER");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(30L);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(response).setHeader("X-RateLimit-Role", "REVIEWER");
        verify(response).setHeader("X-RateLimit-Limit", "80");
    }

    @Test
    @DisplayName("THIRD_PARTY user: allowed when under 40/min limit")
    void preHandle_thirdPartyUserUnderLimit_shouldAllow() {
        setSecurityContext("THIRD_PARTY");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(5L);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(response).setHeader("X-RateLimit-Role", "THIRD_PARTY");
        verify(response).setHeader("X-RateLimit-Limit", "40");
    }

    @Test
    @DisplayName("Anonymous user: allowed when under 10/min limit")
    void preHandle_anonymousUserUnderLimit_shouldAllow() {
        // No authentication set — anonymous
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(5L);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(response).setHeader("X-RateLimit-Role", "ANONYMOUS");
        verify(response).setHeader("X-RateLimit-Limit", "10");
    }

    @Test
    @DisplayName("ENTERPRISE user: throws when over 60/min limit")
    void preHandle_enterpriseUserOverLimit_shouldThrow() {
        setSecurityContext("ENTERPRISE");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(61L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, null));
        assertEquals(1010, ex.getCode());
        verify(response).setHeader("X-RateLimit-Remaining", "0");
        verify(response).setHeader("Retry-After", "60");
    }

    @Test
    @DisplayName("ADMIN user: throws when over 200/min limit")
    void preHandle_adminUserOverLimit_shouldThrow() {
        setSecurityContext("ADMIN");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(201L);

        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("Redis failure: gracefully allows request")
    void preHandle_redisFailure_shouldAllow() {
        setSecurityContext("ENTERPRISE");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("Null count from Redis: allows request")
    void preHandle_nullCount_shouldAllow() {
        setSecurityContext("ENTERPRISE");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(null);

        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("Multiple roles: uses first role that has a defined limit")
    void preHandle_multipleRoles_usesFirstMatching() {
        setSecurityContext("ENTERPRISE", "REVIEWER");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(10L);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(response).setHeader("X-RateLimit-Role", "ENTERPRISE");
        verify(response).setHeader("X-RateLimit-Limit", "60");
    }

    @Test
    @DisplayName("Unknown role: falls back to anonymous limit (10/min)")
    void preHandle_unknownRole_shouldFallbackToAnonymous() {
        setSecurityContext("UNKNOWN_ROLE");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(5L);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(response).setHeader("X-RateLimit-Role", "ANONYMOUS");
        verify(response).setHeader("X-RateLimit-Limit", "10");
    }
}
