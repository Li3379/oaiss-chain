package com.oaiss.chain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * RedisLockService 单元测试
 * RedisLockService Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class RedisLockServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisLockService redisLockService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("尝试获取锁成功")
    void testTryLockSuccess() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        // When
        String lockValue = redisLockService.tryLock("test-lock");

        // Then
        assertNotNull(lockValue);
        verify(valueOperations, times(1)).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("尝试获取锁失败")
    void testTryLockFail() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        // When
        String lockValue = redisLockService.tryLock("test-lock");

        // Then
        assertNull(lockValue);
    }

    @Test
    @DisplayName("尝试获取锁-自定义过期时间")
    void testTryLockWithCustomExpireTime() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        // When
        String lockValue = redisLockService.tryLock("test-lock", 60, TimeUnit.SECONDS);

        // Then
        assertNotNull(lockValue);
    }

    @Test
    @DisplayName("释放锁成功")
    void testReleaseLockSuccess() {
        // Given
        String lockValue = "test-lock-value";
        when(redisTemplate.execute(any(), anyList(), eq(lockValue))).thenReturn(1L);

        // When
        boolean result = redisLockService.releaseLock("test-lock", lockValue);

        // Then
        assertTrue(result);
        verify(redisTemplate, times(1)).execute(any(), eq(List.of("oaiss:lock:test-lock")), eq(lockValue));
        verify(valueOperations, never()).get(anyString());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("释放锁失败-锁值不匹配")
    void testReleaseLockFailValueMismatch() {
        // Given
        String lockValue = "test-lock-value";
        when(redisTemplate.execute(any(), anyList(), eq(lockValue))).thenReturn(0L);

        // When
        boolean result = redisLockService.releaseLock("test-lock", lockValue);

        // Then
        assertFalse(result);
        verify(valueOperations, never()).get(anyString());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("释放锁失败-锁值为null")
    void testReleaseLockFailNullValue() {
        // When
        boolean result = redisLockService.releaseLock("test-lock", null);

        // Then
        assertFalse(result);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("检查锁存在")
    void testIsLockedTrue() {
        // Given
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        // When
        boolean result = redisLockService.isLocked("test-lock");

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("检查锁不存在")
    void testIsLockedFalse() {
        // Given
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // When
        boolean result = redisLockService.isLocked("test-lock");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("强制释放锁成功")
    void testForceReleaseLockSuccess() {
        // Given
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // When
        boolean result = redisLockService.forceReleaseLock("test-lock");

        // Then
        assertTrue(result);
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    @DisplayName("强制释放锁失败")
    void testForceReleaseLockFail() {
        // Given
        when(redisTemplate.delete(anyString())).thenReturn(false);

        // When
        boolean result = redisLockService.forceReleaseLock("test-lock");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("带重试获取锁时使用独立的等待/过期时间单位")
    void testTryLockWithRetryUsesIndependentUnits() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        // When
        String lockValue = redisLockService.tryLockWithRetry(
                "test-lock",
                1000,
                TimeUnit.MILLISECONDS,
                60,
                TimeUnit.SECONDS
        );

        // Then
        assertNotNull(lockValue);
        verify(valueOperations).setIfAbsent(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("带重试获取锁-超时后返回null")
    void testTryLockWithRetryTimeout() {
        // Given - always fail to acquire lock
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        // When - very short wait time
        String lockValue = redisLockService.tryLockWithRetry(
                "test-lock",
                1,
                TimeUnit.MILLISECONDS,
                30,
                TimeUnit.SECONDS
        );

        // Then
        assertNull(lockValue);
    }

    @Test
    @DisplayName("带重试获取锁-线程中断后返回null")
    void testTryLockWithRetryInterrupted() throws Exception {
        // Given - lock acquisition always fails
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        // When - interrupt the current thread during retry
        Thread.currentThread().interrupt();
        String lockValue = redisLockService.tryLockWithRetry(
                "test-lock",
                5000,
                TimeUnit.MILLISECONDS,
                30,
                TimeUnit.SECONDS
        );

        // Then
        assertNull(lockValue);
        // Clean up interrupt flag
        assertTrue(Thread.interrupted());
    }

    @Test
    @DisplayName("释放锁失败-redisTemplate.execute返回null")
    void testReleaseLockFailExecuteReturnsNull() {
        // Given
        String lockValue = "test-lock-value";
        when(redisTemplate.execute(any(), anyList(), eq(lockValue))).thenReturn(null);

        // When
        boolean result = redisLockService.releaseLock("test-lock", lockValue);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("带重试获取锁-循环体内锁失败后重试再超时")
    void testTryLockWithRetryLoopBodyExecutesBeforeTimeout() {
        // Given - always fail to acquire lock, but use enough wait time to enter loop body
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        // When - wait long enough for at least one retry iteration
        String lockValue = redisLockService.tryLockWithRetry(
                "test-lock",
                250,
                TimeUnit.MILLISECONDS,
                30,
                TimeUnit.SECONDS
        );

        // Then
        assertNull(lockValue);
        // Verify tryLock was called at least once inside the loop
        verify(valueOperations, atLeastOnce()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("尝试获取锁-setIfAbsent返回null视为失败")
    void testTryLockSetIfAbsentReturnsNull() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(null);

        // When
        String lockValue = redisLockService.tryLock("test-lock");

        // Then
        assertNull(lockValue);
    }

    @Test
    @DisplayName("释放锁-deleted大于0时返回true")
    void testReleaseLockDeletedGreaterThanZero() {
        // Given
        String lockValue = "test-lock-value";
        when(redisTemplate.execute(any(), anyList(), eq(lockValue))).thenReturn(2L);

        // When
        boolean result = redisLockService.releaseLock("test-lock", lockValue);

        // Then
        assertTrue(result);
    }
}
