package com.oaiss.chain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.HashOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachePreloadServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private CachePreloadService cachePreloadService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("缓存预热-用户类型缓存不存在时创建")
    void testPreloadUserTypeCacheNotExists() {
        // All caches don't exist
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        cachePreloadService.preloadCacheOnStartup();

        // Verify that expire was called for user_types cache (24 hours)
        verify(redisTemplate).expire(eq("user_types:all"), anyLong(), any());
    }

    @Test
    @DisplayName("缓存预热-用户类型缓存已存在时跳过")
    void testPreloadUserTypeCacheExists() {
        when(redisTemplate.hasKey("user_types:all")).thenReturn(true);

        cachePreloadService.preloadCacheOnStartup();

        verify(setOperations, never()).add(eq("user_types:all"), any());
    }

    @Test
    @DisplayName("缓存预热-所有缓存已存在时跳过创建")
    void testPreloadCacheAllExists() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        cachePreloadService.preloadCacheOnStartup();

        verify(setOperations, never()).add(anyString(), any());
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    @DisplayName("手动刷新所有缓存")
    void testRefreshAllCaches() {
        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCacheNames()).thenReturn(List.of("cache1", "cache2"));
        when(cacheManager.getCache("cache1")).thenReturn(mockCache);
        when(cacheManager.getCache("cache2")).thenReturn(mockCache);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        cachePreloadService.refreshAllCaches();

        verify(mockCache, times(2)).clear();
    }

    @Test
    @DisplayName("手动刷新缓存-空缓存列表")
    void testRefreshAllCachesEmpty() {
        when(cacheManager.getCacheNames()).thenReturn(List.of());
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        cachePreloadService.refreshAllCaches();

        assertNotNull(cachePreloadService);
    }

    @Test
    @DisplayName("获取缓存统计信息")
    void testGetCacheStatistics() {
        when(cacheManager.getCacheNames()).thenReturn(List.of("cache1", "cache2"));
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> 2L);

        CachePreloadService.CacheStatistics stats = cachePreloadService.getCacheStatistics();

        assertNotNull(stats);
        assertEquals(2, stats.totalCaches());
        assertTrue(stats.totalKeys() >= 0);
        verify(redisTemplate, atLeastOnce()).execute(any(RedisCallback.class));
    }

    @Test
    @DisplayName("获取缓存统计信息-空缓存")
    void testGetCacheStatisticsEmpty() {
        when(cacheManager.getCacheNames()).thenReturn(List.of());

        CachePreloadService.CacheStatistics stats = cachePreloadService.getCacheStatistics();

        assertNotNull(stats);
        assertEquals(0, stats.totalCaches());
        assertEquals(0, stats.totalKeys());
    }

    @Test
    @DisplayName("缓存统计摘要")
    void testCacheStatisticsSummary() {
        CachePreloadService.CacheStatistics stats = new CachePreloadService.CacheStatistics(5, 100);

        String summary = stats.toSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("5"));
        assertTrue(summary.contains("100"));
    }

    @Test
    @DisplayName("缓存预热-异常处理")
    void testPreloadCacheWithException() {
        when(redisTemplate.hasKey("user_types:all")).thenReturn(false);
        when(setOperations.add(anyString(), any())).thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> cachePreloadService.preloadCacheOnStartup());
    }

    // ==================== Additional coverage tests ====================

    @Test
    @DisplayName("缓存预热-系统配置缓存不存在时创建")
    void testPreloadSystemConfigCacheNotExists() {
        when(redisTemplate.hasKey("user_types:all")).thenReturn(true);
        when(redisTemplate.hasKey("system:config")).thenReturn(false);
        when(redisTemplate.hasKey("permissions:all")).thenReturn(true);
        when(redisTemplate.hasKey("emission_factors:default")).thenReturn(true);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        cachePreloadService.preloadCacheOnStartup();

        verify(hashOperations).put(eq("system:config"), eq("carbon_unit"), eq("tCO2e"));
        verify(hashOperations).put(eq("system:config"), eq("currency_unit"), eq("CNY"));
        verify(hashOperations).put(eq("system:config"), eq("report_period"), eq("YEARLY"));
        verify(hashOperations).put(eq("system:config"), eq("credit_initial_score"), eq("100"));
    }

    @Test
    @DisplayName("缓存预热-权限列表缓存不存在时创建")
    void testPreloadPermissionCacheNotExists() {
        when(redisTemplate.hasKey("user_types:all")).thenReturn(true);
        when(redisTemplate.hasKey("system:config")).thenReturn(true);
        when(redisTemplate.hasKey("permissions:all")).thenReturn(false);
        when(redisTemplate.hasKey("emission_factors:default")).thenReturn(true);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        cachePreloadService.preloadCacheOnStartup();

        verify(setOperations).add(eq("permissions:all"), any(Object[].class));
    }

    @Test
    @DisplayName("缓存预热-排放因子缓存不存在时创建")
    void testPreloadEmissionFactorCacheNotExists() {
        when(redisTemplate.hasKey("user_types:all")).thenReturn(true);
        when(redisTemplate.hasKey("system:config")).thenReturn(true);
        when(redisTemplate.hasKey("permissions:all")).thenReturn(true);
        when(redisTemplate.hasKey("emission_factors:default")).thenReturn(false);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        cachePreloadService.preloadCacheOnStartup();

        verify(hashOperations).put(eq("emission_factors:default"), eq("electricity_grid"), eq("0.5839"));
        verify(hashOperations).put(eq("emission_factors:default"), eq("natural_gas"), eq("2.1620"));
        verify(hashOperations).put(eq("emission_factors:default"), eq("gasoline"), eq("2.9848"));
        verify(hashOperations).put(eq("emission_factors:default"), eq("diesel"), eq("3.1809"));
        verify(hashOperations).put(eq("emission_factors:default"), eq("coal"), eq("2.6600"));
    }

    @Test
    @DisplayName("获取缓存统计信息-keyCount为null时处理")
    void testGetCacheStatisticsWithNullKeyCount() {
        when(cacheManager.getCacheNames()).thenReturn(List.of("cache1"));
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        CachePreloadService.CacheStatistics stats = cachePreloadService.getCacheStatistics();

        assertNotNull(stats);
        assertEquals(1, stats.totalCaches());
        assertEquals(0, stats.totalKeys());
    }

    @Test
    @DisplayName("手动刷新缓存-部分缓存返回null")
    void testRefreshAllCachesWithNullCache() {
        when(cacheManager.getCacheNames()).thenReturn(List.of("cache1", "cache2"));
        when(cacheManager.getCache("cache1")).thenReturn(mock(Cache.class));
        when(cacheManager.getCache("cache2")).thenReturn(null); // null cache
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertDoesNotThrow(() -> cachePreloadService.refreshAllCaches());
    }

    @Test
    @DisplayName("缓存预热-系统配置缓存异常时继续")
    void testPreloadSystemConfigCacheException() {
        when(redisTemplate.hasKey("user_types:all")).thenReturn(true);
        when(redisTemplate.hasKey("system:config")).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("Redis error"))
                .when(hashOperations).put(anyString(), any(), any());

        assertDoesNotThrow(() -> cachePreloadService.preloadCacheOnStartup());
    }

    @Test
    @DisplayName("缓存预热-权限缓存异常时继续")
    void testPreloadPermissionCacheException() {
        when(redisTemplate.hasKey("user_types:all")).thenReturn(true);
        when(redisTemplate.hasKey("system:config")).thenReturn(true);
        when(redisTemplate.hasKey("permissions:all")).thenReturn(false);
        when(setOperations.add(anyString(), any())).thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> cachePreloadService.preloadCacheOnStartup());
    }

    @Test
    @DisplayName("缓存预热-排放因子缓存异常时继续")
    void testPreloadEmissionFactorCacheException() {
        when(redisTemplate.hasKey("user_types:all")).thenReturn(true);
        when(redisTemplate.hasKey("system:config")).thenReturn(true);
        when(redisTemplate.hasKey("permissions:all")).thenReturn(true);
        when(redisTemplate.hasKey("emission_factors:default")).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("Redis error"))
                .when(hashOperations).put(eq("emission_factors:default"), any(), any());

        assertDoesNotThrow(() -> cachePreloadService.preloadCacheOnStartup());
    }
}