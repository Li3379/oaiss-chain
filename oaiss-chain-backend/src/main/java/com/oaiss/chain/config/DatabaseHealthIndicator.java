package com.oaiss.chain.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 数据库健康检查指示器
 * Database Health Check Indicator for Actuator
 * 
 * @author OAISS Team
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return Health.up()
                        .withDetail("database", "MySQL")
                        .withDetail("validation", "passed")
                        .build();
            } else {
                return Health.down()
                        .withDetail("database", "MySQL")
                        .withDetail("validation", "failed")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "MySQL")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
