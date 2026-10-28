package com.oaiss.chain.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Redis健康检查指示器
 * Redis Health Check Indicator for Actuator
 * 
 * @author OAISS Team
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        try {
            // 尝试获取Redis连接并执行PING命令
            String result = redisConnectionFactory.getConnection().ping();
            if ("PONG".equalsIgnoreCase(result)) {
                return Health.up()
                        .withDetail("redis", "connected")
                        .withDetail("response", result)
                        .build();
            } else {
                return Health.down()
                        .withDetail("redis", "unexpected response")
                        .withDetail("response", result)
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("redis", "disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
