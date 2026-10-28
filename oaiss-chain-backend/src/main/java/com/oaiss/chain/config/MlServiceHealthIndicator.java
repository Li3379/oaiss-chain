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
            return Health.down()
                    .withDetail("ml-service", "unavailable")
                    .withDetail("url", mlServiceConfig.getUrl())
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
