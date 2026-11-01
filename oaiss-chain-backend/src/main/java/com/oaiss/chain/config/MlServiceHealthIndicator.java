package com.oaiss.chain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ML服务健康检查指示器
 * ML Service Health Check Indicator for Actuator
 *
 * @author OAISS Team
 */
@Slf4j
@Component
public class MlServiceHealthIndicator implements HealthIndicator {

    private final WebClient mlWebClient;
    private final MlServiceConfig mlServiceConfig;

    public MlServiceHealthIndicator(WebClient mlWebClient, MlServiceConfig mlServiceConfig) {
        this.mlWebClient = mlWebClient;
        this.mlServiceConfig = mlServiceConfig;
    }

    @Override
    public Health health() {
        try {
            String response = mlWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .block();
            return Health.up()
                    .withDetail("ml-service", "available")
                    .withDetail("url", mlServiceConfig.getUrl())
                    .withDetail("response", response)
                    .build();
        } catch (Exception e) {
            log.warn("ML service health check failed: {}", e.getMessage());
            // ML 是**可选**依赖：推理能力缺席不等于整个应用不健康。
            // 早先这里返回 DOWN，后果是只要推理服务没起，聚合健康状态就是
            // DOWN——K8s 探针会反复重启 Pod，CI 的「等待后端就绪」也永远超时，
            // 而实际上碳核算、交易、存证这些主链路都完全正常。
            // UNKNOWN 表达「该项无法判定/未启用」，不污染整体状态。
            return Health.unknown()
                    .withDetail("ml-service", "unavailable")
                    .withDetail("url", mlServiceConfig.getUrl())
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
