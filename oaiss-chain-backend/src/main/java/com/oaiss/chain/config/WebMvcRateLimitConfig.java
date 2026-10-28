package com.oaiss.chain.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for rate limiting interceptors.
 *
 * Registers the RoleBasedRateLimitInterceptor BEFORE any other interceptors
 * so that global per-role limits are enforced first, before endpoint-specific
 * limits applied by the RateLimitAspect.
 *
 * Conditional on {@code oaiss.ratelimit.enabled} — same switch as
 * {@link RoleBasedRateLimitInterceptor} and {@code RateLimitAspect} — so that
 * test slices with rate limiting disabled skip interceptor registration.
 *
 * @author OAISS Team
 */
@Configuration
@ConditionalOnProperty(name = "oaiss.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class WebMvcRateLimitConfig implements WebMvcConfigurer {

    private final RoleBasedRateLimitInterceptor roleBasedRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleBasedRateLimitInterceptor)
                .addPathPatterns("/api/**")
                .order(0); // Execute before other interceptors
    }
}
