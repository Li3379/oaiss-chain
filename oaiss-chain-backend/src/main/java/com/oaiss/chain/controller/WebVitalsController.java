package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.annotation.AuditLog;

import java.util.List;

/**
 * ==============================================================================
 * 【业务模块】：22. 前端核心性能观测与体验指标打点 (Frontend Performance & Core Web Vitals)
 * 【服务角色】：全系统前端性能监控与遥测观测
 * 【对应前端页面与路由】：
 *   - 全局性能打点: main.ts / 性能探针上报 (LCP, FID, CLS, FCP, TTFB, INP)
 * 【调用前端 API 模块】：全局遥测上报 -> POST /web-vitals/report
 * ==============================================================================
 */
@Slf4j
@RestController
@RequestMapping("/web-vitals")
@Tag(name = "21. Web Vitals", description = "Frontend performance metrics collection (Core Web Vitals)")
public class WebVitalsController {

    /**
     * 【业务功能】：接收前端上报的核心体验性能指标数据（页面加载延迟、交互响应度、布局偏移）
     * 【对应前端页面】：全系统任意路由页面加载与交互时由浏览器性能观测器静默上报
     * 【可见UI操作】：前端页面无感打点，用于监控保障系统交互流畅度与渲染稳定性
     */
    @AuditLog(module = "系统监控", action = "REPORT_VITALS", description = "上报Web性能指标")
    @PostMapping("/report")
    @RateLimit(key = "web_vitals", limit = 30, period = 60)
    @Operation(summary = "Report Web Vitals", description = "Receive frontend Core Web Vitals metrics (LCP, FID, CLS, FCP, TTFB)")
    public ApiResponse<Void> reportVitals(@RequestBody List<WebVitalMetric> metrics) {
        for (WebVitalMetric metric : metrics) {
            log.info("WebVital: name={}, value={}, rating={}, path={}", 
                    metric.getName(), metric.getValue(), metric.getRating(), metric.getPath());
            
            // Record to Micrometer for Prometheus export
            // This allows correlating frontend performance with backend metrics
            recordMetric(metric);
        }
        return ApiResponse.success(null);
    }

    private void recordMetric(WebVitalMetric metric) {
        // Log structured metrics for analysis
        log.info("WEB_VITALS_METRIC|{}|{}|{}|{}|{}", 
                metric.getName(), 
                String.format("%.2f", metric.getValue()),
                metric.getRating(),
                metric.getPath() != null ? metric.getPath() : "unknown",
                metric.getTimestamp());
    }

    /**
     * Web Vital metric from frontend
     */
    public static class WebVitalMetric {
        private String name;
        private double value;
        private String rating;
        private String id;
        private String navigationType;
        private long timestamp;
        private String path;
        public WebVitalMetric() {}
        public String getName() { return name; }
        public double getValue() { return value; }
        public String getRating() { return rating; }
        public String getId() { return id; }
        public String getNavigationType() { return navigationType; }
        public long getTimestamp() { return timestamp; }
        public String getPath() { return path; }
    }
}
