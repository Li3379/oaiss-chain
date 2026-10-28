package com.oaiss.chain.config;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Role-Based Rate Limiting Interceptor
 *
 * Applies different rate limits per user role using Redis sliding-window counters.
 * This interceptor is registered BEFORE the annotation-based RateLimitAspect
 * so that global per-role limits are enforced first.
 *
 * Rate limits (requests per 60-second window):
 *   ADMIN      — 200/min
 *   REVIEWER   — 80/min
 *   ENTERPRISE — 60/min
 *   THIRD_PARTY— 40/min
 *   ANONYMOUS  — 10/min
 *
 * @author OAISS Team
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "oaiss.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RoleBasedRateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Long> redisScriptTemplate;

    /** Redis key prefix for role-based rate limiting */
    private static final String KEY_PREFIX = "rate_limit:role:";

    /** Default time window in seconds */
    private static final int PERIOD_SECONDS = 60;

    /** Lua script for atomic INCR + EXPIRE */
    private static final String LUA_INCR_EXPIRE = """
            local current = redis.call('incr', KEYS[1])
            if current == 1 then
                redis.call('expire', KEYS[1], ARGV[1])
            end
            return current
            """;

    /**
     * Role → max requests per window.
     * Uses an immutable map for thread-safe lookups.
     */
    private static final Map<String, Integer> ROLE_LIMITS = Map.of(
            "ADMIN",       200,
            "REVIEWER",     80,
            "ENTERPRISE",   60,
            "THIRD_PARTY",  40
    );

    /** Limit for unauthenticated / unknown roles */
    private static final int ANONYMOUS_LIMIT = 10;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // Resolve the user's primary role and the corresponding limit
        String role = resolvePrimaryRole();
        int limit = ROLE_LIMITS.getOrDefault(role, ANONYMOUS_LIMIT);

        // Build a unique Redis key per role (not per user — global role throttle)
        String redisKey = KEY_PREFIX + role;

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_INCR_EXPIRE, Long.class);
            Long currentCount = redisScriptTemplate.execute(
                    script, Collections.singletonList(redisKey), String.valueOf(PERIOD_SECONDS));

            if (currentCount != null && currentCount > limit) {
                log.warn("Role-based rate limit exceeded for role={}, count={}, limit={}",
                        role, currentCount, limit);

                response.setHeader("X-RateLimit-Role", role);
                response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("Retry-After", String.valueOf(PERIOD_SECONDS));

                throw new BusinessException(ErrorCode.REQUEST_TOO_FREQUENT,
                        "Role rate limit exceeded for " + role);
            }

            // Set informational headers on successful requests
            long remaining = Math.max(0, limit - (currentCount != null ? currentCount : 0));
            response.setHeader("X-RateLimit-Role", role);
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset",
                    String.valueOf(System.currentTimeMillis() / 1000 + PERIOD_SECONDS));

            return true;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Degrade gracefully: if Redis is down, allow the request
            log.error("Role-based rate limit check failed, allowing request", e);
            return true;
        }
    }

    /**
     * Resolve the current user's primary role from the Spring Security context.
     * Returns "ANONYMOUS" if no authenticated user is present.
     */
    private String resolvePrimaryRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "ANONYMOUS";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtUserDetails userDetails) {
            List<String> roles = userDetails.getRoles();
            if (roles != null && !roles.isEmpty()) {
                // Return the first matching role that has a defined limit
                for (String r : roles) {
                    if (ROLE_LIMITS.containsKey(r)) {
                        return r;
                    }
                }
            }
        }

        return "ANONYMOUS";
    }
}
