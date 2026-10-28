package com.oaiss.chain.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * CSP Nonce Filter
 *
 * Generates a unique cryptographic nonce per request and:
 * 1. Adds it as a request attribute for template engines
 * 2. Sets the Content-Security-Policy header with nonce-based directives
 *
 * This replaces 'unsafe-inline' in style-src with a per-request nonce,
 * significantly improving XSS protection while maintaining compatibility
 * with Element Plus dynamic style injection.
 *
 * @author OAISS Chain Team
 * @since 1.3.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CspNonceFilter extends OncePerRequestFilter {

    private static final String NONCE_ATTRIBUTE = "cspNonce";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String nonce = generateNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);

        String cspPolicy = String.format(
                "default-src 'self'; "
                + "script-src 'self' 'nonce-%s'; "
                + "style-src 'self' 'nonce-%s'; "
                + "img-src 'self' data: blob:; "
                + "font-src 'self'; "
                + "connect-src 'self'; "
                + "frame-ancestors 'none'",
                nonce, nonce);

        response.setHeader("Content-Security-Policy", cspPolicy);

        filterChain.doFilter(request, response);
    }

    /**
     * Generate a cryptographically random nonce (128 bits, base64-encoded).
     */
    private String generateNonce() {
        byte[] nonceBytes = new byte[16];
        SECURE_RANDOM.nextBytes(nonceBytes);
        return Base64.getEncoder().encodeToString(nonceBytes);
    }

    /**
     * Utility to retrieve the nonce from a request attribute.
     */
    public static String getNonce(HttpServletRequest request) {
        Object nonce = request.getAttribute(NONCE_ATTRIBUTE);
        return nonce != null ? nonce.toString() : "";
    }
}
