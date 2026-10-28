package com.oaiss.chain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP cache configuration.
 * - ETag: shallow ETag for all GET responses (304 support)
 * - Cache-Control: per-URL-pattern cache headers via interceptor
 *   - /captcha/**, /auth/** → no-store (sensitive)
 *   - /health → no-cache (probes need fresh data)
 *   - Other GET → private, max-age=30 (short browser cache)
 *   - Non-GET → no-store
 *
 * @author OAISS Team
 */
@Configuration
public class HttpCacheConfig implements WebMvcConfigurer {

    @Bean
    public Filter shallowEtagHeaderFilter() {
        return new ShallowEtagHeaderFilter();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CacheControlInterceptor())
                .addPathPatterns("/api/v1/**");
    }

    private static class CacheControlInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                 Object handler) {
            String uri = request.getRequestURI();
            String method = request.getMethod();

            if (!"GET".equalsIgnoreCase(method)) {
                response.setHeader("Cache-Control", "no-store");
                return true;
            }

            if (uri.contains("/captcha") || uri.contains("/auth/") || uri.contains("/actuator")) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            } else if (uri.contains("/health")) {
                response.setHeader("Cache-Control", "no-cache, max-age=0");
            } else {
                response.setHeader("Cache-Control", "private, max-age=30, must-revalidate");
            }
            return true;
        }
    }

    /**
     * R117: Security headers filter for API responses.
     * Adds standard security headers to all API responses.
     */
    @Bean
    public FilterRegistrationBean<jakarta.servlet.Filter> securityHeadersFilter() {
        FilterRegistrationBean<jakarta.servlet.Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new jakarta.servlet.Filter() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res,
                                 jakarta.servlet.FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
                jakarta.servlet.http.HttpServletResponse response = (jakarta.servlet.http.HttpServletResponse) res;
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("X-XSS-Protection", "1; mode=block");
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
                chain.doFilter(req, res);
            }
        });
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

}