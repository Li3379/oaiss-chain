package com.oaiss.chain.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Trace ID Filter
 * Ensures every request has a traceId in MDC for log correlation.
 * If Micrometer Tracing provides one, it is used; otherwise generates a UUID.
 * Also adds X-Trace-Id response header for client-side debugging.
 *
 * @author OAISS Team
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put(TRACE_ID_KEY, traceId);
        }
        MDC.put(REQUEST_ID_KEY, traceId);
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-API-Version", "1");

        long startTime = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            response.setHeader("X-Response-Time", durationMs + "ms");
            MDC.put("responseTimeMs", String.valueOf(durationMs));
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove("responseTimeMs");
            // Don't remove traceId - Micrometer manages it
        }
    }
}