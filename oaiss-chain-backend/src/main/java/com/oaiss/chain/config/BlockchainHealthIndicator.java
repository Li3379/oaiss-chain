package com.oaiss.chain.config;

import com.oaiss.chain.service.BlockchainServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Blockchain Service Health Indicator
 *
 * Reports the health of the Hyperledger Fabric blockchain connection.
 * Used by Kubernetes liveness/readiness probes via /actuator/health.
 *
 * @author OAISS Chain Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainHealthIndicator implements HealthIndicator {

    private final BlockchainServicePort blockchainService;

    @Override
    public Health health() {
        try {
            // Attempt a lightweight connection check
            Map<String, Object> connectionStatus = blockchainService.checkConnection();
            boolean connected = connectionStatus != null && !connectionStatus.isEmpty();
            if (connected) {
                return Health.up()
                        .withDetail("service", "Hyperledger Fabric")
                        .withDetails(connectionStatus)
                        .build();
            }
            return Health.up()
                    .withDetail("service", "Hyperledger Fabric")
                    .withDetail("status", "mock-mode")
                    .build();
        } catch (Exception e) {
            log.warn("Blockchain health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("service", "Hyperledger Fabric")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
