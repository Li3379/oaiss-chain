package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DatabaseHealthIndicator 单元测试
 * DatabaseHealthIndicator Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class DatabaseHealthIndicatorTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @InjectMocks
    private DatabaseHealthIndicator healthIndicator;

    @Test
    @DisplayName("健康检查成功-连接有效")
    void testHealthSuccess() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertEquals("MySQL", health.getDetails().get("database"));
        assertEquals("passed", health.getDetails().get("validation"));
    }

    @Test
    @DisplayName("健康检查失败-连接无效")
    void testHealthFailInvalidConnection() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("MySQL", health.getDetails().get("database"));
        assertEquals("failed", health.getDetails().get("validation"));
    }

    @Test
    @DisplayName("健康检查失败-SQL异常")
    void testHealthFailSQLException() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("MySQL", health.getDetails().get("database"));
        assertNotNull(health.getDetails().get("error"));
    }

    @Test
    @DisplayName("健康检查失败-连接关闭异常")
    void testHealthFailConnectionCloseException() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        doThrow(new SQLException("Close error")).when(connection).close();

        // When
        Health health = healthIndicator.health();

        // Then - When close throws, the exception is caught and returns DOWN
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("MySQL", health.getDetails().get("database"));
        assertNotNull(health.getDetails().get("error"));
    }

    @Test
    @DisplayName("健康检查失败-连接无效且关闭异常")
    void testHealthFailInvalidThenCloseException() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);
        doThrow(new SQLException("Close error")).when(connection).close();

        // When
        Health health = healthIndicator.health();

        // Then - isValid returns false (else branch), then close throws -> catch
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("MySQL", health.getDetails().get("database"));
        assertNotNull(health.getDetails().get("error"));
    }
}
