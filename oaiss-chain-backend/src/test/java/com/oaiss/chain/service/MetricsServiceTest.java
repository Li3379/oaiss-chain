package com.oaiss.chain.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsService 单元测试
 * 使用 SimpleMeterRegistry 进行测试，避免 mock 复杂性
 */
class MetricsServiceTest {

    private MeterRegistry meterRegistry;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        // Use SimpleMeterRegistry instead of mock for proper counter/gauge support
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    @DisplayName("记录API请求")
    void testIncrementApiRequest() {
        metricsService.incrementApiRequest("GET", "/api/users", 200);
        metricsService.incrementApiRequest("POST", "/api/users", 201);
        metricsService.incrementApiRequest("GET", "/api/users/123", 404);

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("记录碳信用注册")
    void testIncrementCarbonCreditRegistration() {
        metricsService.incrementCarbonCreditRegistration("success");
        metricsService.incrementCarbonCreditRegistration("pending");
        metricsService.incrementCarbonCreditRegistration("failed");

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("记录碳交易")
    void testIncrementTrade() {
        metricsService.incrementTrade("completed");
        metricsService.incrementTrade("pending");
        metricsService.incrementTrade("cancelled");

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("记录用户登录")
    void testIncrementUserLogin() {
        metricsService.incrementUserLogin("ENTERPRISE", true);
        metricsService.incrementUserLogin("ENTERPRISE", false);
        metricsService.incrementUserLogin("ADMIN", true);

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("开始计时")
    void testStartTimer() {
        Timer.Sample sample = metricsService.startTimer();
        assertNotNull(sample);
    }

    @Test
    @DisplayName("记录固定耗时")
    void testRecordDuration() {
        metricsService.recordDuration("api_call", Duration.ofMillis(100));
        metricsService.recordDuration("database_query", Duration.ofMillis(50));

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("记录错误")
    void testIncrementError() {
        metricsService.incrementError("validation", "Invalid parameter");
        metricsService.incrementError("database", "Connection failed");
        metricsService.incrementError("auth", "Token expired");

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("更新活跃用户数")
    void testUpdateActiveUsers() {
        metricsService.updateActiveUsers(100);
        metricsService.updateActiveUsers(50);

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("增加活跃用户")
    void testIncrementActiveUsers() {
        metricsService.incrementActiveUsers();
        metricsService.incrementActiveUsers();
        metricsService.incrementActiveUsers();

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("减少活跃用户")
    void testDecrementActiveUsers() {
        metricsService.decrementActiveUsers();
        metricsService.decrementActiveUsers();

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("更新待审核碳信用数量")
    void testUpdatePendingCarbonCredit() {
        metricsService.updatePendingCarbonCredit(10);
        metricsService.updatePendingCarbonCredit(5);

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("更新待审核交易数量")
    void testUpdatePendingTrade() {
        metricsService.updatePendingTrade(20);
        metricsService.updatePendingTrade(15);

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("空路径处理")
    void testNullPath() {
        metricsService.incrementApiRequest("GET", null, 200);

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("空消息处理")
    void testNullMessage() {
        metricsService.incrementError("test", null);

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("多种HTTP方法")
    void testVariousHttpMethods() {
        metricsService.incrementApiRequest("GET", "/api/resource", 200);
        metricsService.incrementApiRequest("POST", "/api/resource", 201);
        metricsService.incrementApiRequest("PUT", "/api/resource/1", 200);
        metricsService.incrementApiRequest("DELETE", "/api/resource/1", 204);
        metricsService.incrementApiRequest("PATCH", "/api/resource/1", 200);

        assertNotNull(metricsService);
    }

    @Test
    @DisplayName("多种HTTP状态码")
    void testVariousStatusCodes() {
        metricsService.incrementApiRequest("GET", "/api/resource", 200);
        metricsService.incrementApiRequest("GET", "/api/resource", 201);
        metricsService.incrementApiRequest("GET", "/api/resource", 400);
        metricsService.incrementApiRequest("GET", "/api/resource", 401);
        metricsService.incrementApiRequest("GET", "/api/resource", 403);
        metricsService.incrementApiRequest("GET", "/api/resource", 404);
        metricsService.incrementApiRequest("GET", "/api/resource", 500);

        assertNotNull(metricsService);
    }
}