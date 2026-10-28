package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedisHealthIndicator 单元测试
 * RedisHealthIndicator Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class RedisHealthIndicatorTest {

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @InjectMocks
    private RedisHealthIndicator healthIndicator;

    @Test
    @DisplayName("健康检查成功-PONG响应")
    void testHealthSuccess() {
        // Given
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertEquals("connected", health.getDetails().get("redis"));
    }

    @Test
    @DisplayName("健康检查成功-小写pong响应")
    void testHealthSuccessLowercase() {
        // Given
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("pong");

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertEquals("connected", health.getDetails().get("redis"));
    }

    @Test
    @DisplayName("健康检查失败-意外响应")
    void testHealthFailUnexpectedResponse() {
        // Given
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("UNEXPECTED");

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("unexpected response", health.getDetails().get("redis"));
    }

    @Test
    @DisplayName("健康检查失败-连接异常")
    void testHealthFailConnectionException() {
        // Given
        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("disconnected", health.getDetails().get("redis"));
        assertNotNull(health.getDetails().get("error"));
    }

    @Test
    @DisplayName("健康检查失败-null响应抛出异常")
    void testHealthFailNullResponse() {
        // Given
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn(null);

        // When
        Health health = healthIndicator.health();

        // Then - null response causes NullPointerException which is caught and returns disconnected
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("disconnected", health.getDetails().get("redis"));
    }
}
