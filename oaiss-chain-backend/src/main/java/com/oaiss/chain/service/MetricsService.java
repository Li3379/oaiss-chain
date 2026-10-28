package com.oaiss.chain.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务指标监控服务
 * Business Metrics Service for Prometheus
 * 
 * @author OAISS Team
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    
    // 缓存计数器
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    
    // 业务指标
    private final AtomicLong activeUsers = new AtomicLong(0);
    private final AtomicLong pendingCarbonCredit = new AtomicLong(0);
    private final AtomicLong pendingTrade = new AtomicLong(0);

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 注册业务指标Gauge
        registerBusinessGauges();
        
        log.info("MetricsService initialized with Prometheus registry");
    }

    /**
     * 注册业务Gauge指标
     */
    private void registerBusinessGauges() {
        // 活跃用户数
        Gauge.builder("oaiss.active.users", activeUsers, AtomicLong::get)
                .description("Current number of active users")
                .tag("type", "business")
                .register(meterRegistry);
        
        // 待审核碳信用数量
        Gauge.builder("oaiss.carbon.pending", pendingCarbonCredit, AtomicLong::get)
                .description("Number of pending carbon credits for review")
                .tag("type", "business")
                .register(meterRegistry);
        
        // 待审核交易数量
        Gauge.builder("oaiss.trade.pending", pendingTrade, AtomicLong::get)
                .description("Number of pending trades for review")
                .tag("type", "business")
                .register(meterRegistry);
    }

    // ==================== API请求计数 ====================

    /**
     * 记录API请求
     */
    public void incrementApiRequest(String method, String path, int status) {
        String key = String.format("api.request.%s.%s.%d", method, sanitizePath(path), status);
        counters.computeIfAbsent(key, k -> Counter.builder("oaiss.api.requests")
                .description("API request counter")
                .tag("method", method)
                .tag("path", sanitizePath(path))
                .tag("status", String.valueOf(status))
                .register(meterRegistry))
                .increment();
    }

    // ==================== 业务操作计数 ====================

    /**
     * 记录碳信用注册
     */
    public void incrementCarbonCreditRegistration(String status) {
        Counter.builder("oaiss.carbon.registration")
                .description("Carbon credit registration counter")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录碳交易
     */
    public void incrementTrade(String status) {
        Counter.builder("oaiss.trade.count")
                .description("Carbon trade counter")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录用户登录
     */
    public void incrementUserLogin(String userType, boolean success) {
        Counter.builder("oaiss.user.login")
                .description("User login counter")
                .tag("user_type", userType)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();
    }

    // ==================== 操作耗时 ====================

    /**
     * 记录操作耗时
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * 结束计时
     */
    public void recordTimer(Timer.Sample sample, String operation) {
        sample.stop(Timer.builder("oaiss.operation.duration")
                .description("Operation duration")
                .tag("operation", operation)
                .register(meterRegistry));
    }

    /**
     * 记录固定耗时
     */
    public void recordDuration(String operation, Duration duration) {
        Timer.builder("oaiss.operation.duration")
                .description("Operation duration")
                .tag("operation", operation)
                .register(meterRegistry)
                .record(duration);
    }

    // ==================== 错误计数 ====================

    /**
     * 记录错误
     */
    public void incrementError(String type, String message) {
        Counter.builder("oaiss.errors")
                .description("Application error counter")
                .tag("type", type)
                .tag("message", truncateMessage(message))
                .register(meterRegistry)
                .increment();
    }

    // ==================== 业务状态更新 ====================

    /**
     * 更新活跃用户数
     */
    public void updateActiveUsers(long count) {
        activeUsers.set(count);
    }

    /**
     * 增加活跃用户
     */
    public void incrementActiveUsers() {
        activeUsers.incrementAndGet();
    }

    /**
     * 减少活跃用户
     */
    public void decrementActiveUsers() {
        activeUsers.decrementAndGet();
    }

    /**
     * 更新待审核碳信用数量
     */
    public void updatePendingCarbonCredit(long count) {
        pendingCarbonCredit.set(count);
    }

    /**
     * 更新待审核交易数量
     */
    public void updatePendingTrade(long count) {
        pendingTrade.set(count);
    }

    // ==================== 工具方法 ====================

    /**
     * 清理路径，移除动态ID
     */
    private String sanitizePath(String path) {
        if (path == null) {
            return "unknown";
        }
        // 移除路径中的数字ID
        return path.replaceAll("/\\d+", "/{id}")
                   .replaceAll("[?].*", "");
    }

    /**
     * 截断消息，避免标签过长
     */
    private String truncateMessage(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() > 50 ? message.substring(0, 50) + "..." : message;
    }
}
