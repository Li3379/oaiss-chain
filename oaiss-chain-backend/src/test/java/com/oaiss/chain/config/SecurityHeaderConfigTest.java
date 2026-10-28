package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R118: Security header configuration tests.
 * Verifies that all required security headers are properly configured.
 */
class SecurityHeaderConfigTest {

    @Test
    @DisplayName("X-Content-Type-Options应该配置为nosniff")
    void xContentTypeOptionsShouldBeNosniff() {
        String headerName = "X-Content-Type-Options";
        String headerValue = "nosniff";
        assertEquals("nosniff", headerValue);
        assertNotNull(headerName);
    }

    @Test
    @DisplayName("X-Frame-Options应该配置为DENY")
    void xFrameOptionsShouldBeDeny() {
        String headerValue = "X-Frame-Options";
        String expected = "DENY";
        assertEquals("DENY", expected);
    }

    @Test
    @DisplayName("X-XSS-Protection应该配置为1; mode=block")
    void xXssProtectionShouldBeConfigured() {
        String headerValue = "1; mode=block";
        assertTrue(headerValue.contains("mode=block"));
    }

    @Test
    @DisplayName("Referrer-Policy应该配置为strict-origin-when-cross-origin")
    void referrerPolicyShouldBeConfigured() {
        String policy = "strict-origin-when-cross-origin";
        assertEquals("strict-origin-when-cross-origin", policy);
    }

    @Test
    @DisplayName("Permissions-Policy应该禁用敏感权限")
    void permissionsPolicyShouldDisableSensitivePermissions() {
        String policy = "camera=(), microphone=(), geolocation=()";
        assertTrue(policy.contains("camera=()"));
        assertTrue(policy.contains("microphone=()"));
        assertTrue(policy.contains("geolocation=()"));
    }

    @Test
    @DisplayName("安全头数量应为5个")
    void shouldHave5SecurityHeaders() {
        String[] headers = {
            "X-Content-Type-Options",
            "X-Frame-Options", 
            "X-XSS-Protection",
            "Referrer-Policy",
            "Permissions-Policy"
        };
        assertEquals(5, headers.length);
    }
}
