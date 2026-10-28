package com.oaiss.chain.config;

import com.oaiss.chain.annotation.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R122: RateLimit annotation configuration tests.
 * Verifies annotation defaults and usage patterns.
 */
class RateLimitConfigTest {

    @Test
    @DisplayName("RateLimit默认period应为60秒")
    void defaultPeriodShouldBe60Seconds() throws NoSuchMethodException {
        var method = TestTarget.class.getMethod("defaultLimit");
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        assertNotNull(annotation);
        assertEquals(60, annotation.period());
    }

    @Test
    @DisplayName("RateLimit默认limit应为100")
    void defaultLimitShouldBe100() throws NoSuchMethodException {
        var method = TestTarget.class.getMethod("defaultLimit");
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        assertNotNull(annotation);
        assertEquals(100, annotation.limit());
    }

    @Test
    @DisplayName("RateLimit应支持自定义key")
    void shouldSupportCustomKey() throws NoSuchMethodException {
        var method = TestTarget.class.getMethod("customLimit");
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        assertNotNull(annotation);
        assertEquals("custom:key", annotation.key());
        assertEquals(10, annotation.limit());
        assertEquals(30, annotation.period());
    }

    @Test
    @DisplayName("RateLimit应支持IP限流类型")
    void shouldSupportIpLimitType() throws NoSuchMethodException {
        var method = TestTarget.class.getMethod("ipLimit");
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        assertNotNull(annotation);
        assertEquals(RateLimit.LimitType.IP, annotation.limitType());
    }

    @Test
    @DisplayName("RateLimit应支持用户限流类型")
    void shouldSupportUserLimitType() throws NoSuchMethodException {
        var method = TestTarget.class.getMethod("userLimit");
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        assertNotNull(annotation);
        assertEquals(RateLimit.LimitType.USER, annotation.limitType());
    }

    @Test
    @DisplayName("限流类型枚举应有4种")
    void shouldHave4LimitTypes() {
        RateLimit.LimitType[] types = RateLimit.LimitType.values();
        assertEquals(4, types.length);
    }

    // Test target class with annotated methods
    static class TestTarget {
        @RateLimit
        public void defaultLimit() {}

        @RateLimit(key = "custom:key", limit = 10, period = 30)
        public void customLimit() {}

        @RateLimit(limitType = RateLimit.LimitType.IP)
        public void ipLimit() {}

        @RateLimit(limitType = RateLimit.LimitType.USER)
        public void userLimit() {}
    }
}
