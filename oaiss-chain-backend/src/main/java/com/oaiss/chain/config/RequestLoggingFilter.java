package com.oaiss.chain.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Structured request/response logging filter.
 * Logs method, URI, status, duration, client IP, user-agent, and content length.
 * Skips health-check and actuator endpoints to reduce noise.
 * Sensitive fields (password, token, secret, captcha) are masked in logs.
 *
 * @author OAISS Team
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final List<String> SKIP_PATHS = Arrays.asList(
            "/actuator", "/favicon.ico", "/robots.txt"
    );

    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(password|token|secret|captcha|authorization|cookie)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HEALTH_PATH = Pattern.compile(".*/(health|info|prometheus)$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Skip static resources and health endpoints
        if (HEALTH_PATH.matcher(uri).matches()) return true;
        return SKIP_PATHS.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String clientIp = getClientIp(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            int statusCode = responseWrapper.getStatus();
            int responseSize = responseWrapper.getContentSize();
            String userAgent = maskSensitive(request.getHeader("User-Agent"));

            if (statusCode >= 400) {
                log.warn("HTTP {} {} {} ip={} duration={}ms size={}B ua=\"{}\"",
                        statusCode, method,
                        queryString != null ? uri + "?" + queryString : uri,
                        clientIp, durationMs, responseSize, userAgent);
            } else if (durationMs > 3000) {
                log.warn("SLOW {} {} ip={} duration={}ms size={}B",
                        method, uri, clientIp, durationMs, responseSize);
            } else {
                log.info("HTTP {} {} {} ip={} duration={}ms size={}B",
                        statusCode, method, uri, clientIp, durationMs, responseSize);
            }

            responseWrapper.copyBodyToResponse();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String maskSensitive(String value) {
        if (value == null) return "-";
        if (value.length() > 100) {
            return value.substring(0, 100) + "...";
        }
        return value;
    }
}