package com.oaiss.chain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthenticationException 单元测试
 * AuthenticationException Unit Tests
 */
class AuthenticationExceptionTest {

    @Test
    @DisplayName("创建基本异常")
    void testBasicException() {
        AuthenticationException ex = new AuthenticationException(1001, "Test error");
        assertEquals(1001, ex.getCode());
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("创建带原因的异常")
    void testExceptionWithCause() {
        Throwable cause = new RuntimeException("Original cause");
        AuthenticationException ex = new AuthenticationException(1002, "Test error", cause);
        assertEquals(1002, ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("notLogin工厂方法")
    void testNotLogin() {
        AuthenticationException ex = AuthenticationException.notLogin();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("未登录"));
    }

    @Test
    @DisplayName("tokenInvalid工厂方法")
    void testTokenInvalid() {
        AuthenticationException ex = AuthenticationException.tokenInvalid();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Token无效"));
    }

    @Test
    @DisplayName("tokenExpired工厂方法")
    void testTokenExpired() {
        AuthenticationException ex = AuthenticationException.tokenExpired();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("accountDisabled工厂方法")
    void testAccountDisabled() {
        AuthenticationException ex = AuthenticationException.accountDisabled();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("禁用"));
    }

    @Test
    @DisplayName("captchaError工厂方法")
    void testCaptchaError() {
        AuthenticationException ex = AuthenticationException.captchaError();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("验证码错误"));
    }

    @Test
    @DisplayName("captchaExpired工厂方法")
    void testCaptchaExpired() {
        AuthenticationException ex = AuthenticationException.captchaExpired();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("loginFailed工厂方法")
    void testLoginFailed() {
        AuthenticationException ex = AuthenticationException.loginFailed("Invalid password");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Invalid password"));
    }

    @Test
    @DisplayName("ipValidationFailed工厂方法")
    void testIpValidationFailed() {
        AuthenticationException ex = AuthenticationException.ipValidationFailed();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("IP"));
    }
}
