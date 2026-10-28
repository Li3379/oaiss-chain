package com.oaiss.chain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthorizationException 单元测试
 * AuthorizationException Unit Tests
 */
class AuthorizationExceptionTest {

    @Test
    @DisplayName("创建基本异常")
    void testBasicException() {
        AuthorizationException ex = new AuthorizationException(1001, "Test error");
        assertEquals(1001, ex.getCode());
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("创建带原因的异常")
    void testExceptionWithCause() {
        Throwable cause = new RuntimeException("Original cause");
        AuthorizationException ex = new AuthorizationException(1002, "Test error", cause);
        assertEquals(1002, ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("permissionDenied无参工厂方法")
    void testPermissionDeniedNoArgs() {
        AuthorizationException ex = AuthorizationException.permissionDenied();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("无权限"));
    }

    @Test
    @DisplayName("permissionDenied带消息工厂方法")
    void testPermissionDeniedWithMessage() {
        AuthorizationException ex = AuthorizationException.permissionDenied("Custom message");
        assertNotNull(ex);
        assertEquals("Custom message", ex.getMessage());
    }

    @Test
    @DisplayName("roleNotEnough工厂方法")
    void testRoleNotEnough() {
        AuthorizationException ex = AuthorizationException.roleNotEnough("ADMIN");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("ADMIN"));
    }
}
