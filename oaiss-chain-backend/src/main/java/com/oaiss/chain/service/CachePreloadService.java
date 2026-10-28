package com.oaiss.chain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Async;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热服务
 * Cache Preload Service
 * 
 * 在应用启动完成后自动预热常用缓存数据，减少首次访问延迟
 * Preloads frequently accessed data into cache on application startup
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachePreloadService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 执行缓存预热（实际逻辑，可同步或异步调用）
     * Execute cache preload (core logic, callable sync or async)
     */
    private void doPreloadCaches() {
        log.info("=== 开始缓存预热 (Starting Cache Preload) ===");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 预热用户类型缓存
            preloadUserTypeCache();

            // 2. 预热系统配置缓存
            preloadSystemConfigCache();

            // 3. 预热权限列表缓存
            preloadPermissionCache();

            // 4. 预热碳核算因子缓存
            preloadEmissionFactorCache();

            long duration = System.currentTimeMillis() - startTime;
            log.info("=== 缓存预热完成 (Cache Preload Completed) - 耗时: {}ms ===", duration);

        } catch (Exception e) {
            log.error("缓存预热失败 (Cache Preload Failed): {}", e.getMessage(), e);
        }
    }

    /**
     * 应用启动完成后执行缓存预热（异步）
     * Execute cache preload after application is ready (async)
     */
    @Async("cachePreloadExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void preloadCacheOnStartup() {
        doPreloadCaches();
    }
    
    /**
     * 预热用户类型缓存
     * Preload user type cache
     */
    private void preloadUserTypeCache() {
        String cacheKey = "user_types:all";
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(cacheKey))) {
                // 用户类型枚举值缓存
                Set<String> userTypes = Set.of("ENTERPRISE", "REVIEWER", "THIRD_PARTY", "ADMIN");
                redisTemplate.opsForSet().add(cacheKey, userTypes.toArray());
                redisTemplate.expire(cacheKey, 24, TimeUnit.HOURS);
                log.debug("预热用户类型缓存完成: {} types", userTypes.size());
            }
        } catch (Exception e) {
            log.warn("预热用户类型缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 预热系统配置缓存
     * Preload system configuration cache
     */
    private void preloadSystemConfigCache() {
        String cacheKey = "system:config";
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(cacheKey))) {
                // 系统默认配置
                redisTemplate.opsForHash().put(cacheKey, "carbon_unit", "tCO2e");
                redisTemplate.opsForHash().put(cacheKey, "currency_unit", "CNY");
                redisTemplate.opsForHash().put(cacheKey, "report_period", "YEARLY");
                redisTemplate.opsForHash().put(cacheKey, "credit_initial_score", "100");
                redisTemplate.expire(cacheKey, 12, TimeUnit.HOURS);
                log.debug("预热系统配置缓存完成");
            }
        } catch (Exception e) {
            log.warn("预热系统配置缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 预热权限列表缓存
     * Preload permission list cache
     */
    private void preloadPermissionCache() {
        String cacheKey = "permissions:all";
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(cacheKey))) {
                // 常用权限代码
                Set<String> permissions = Set.of(
                    "carbon:report:create",
                    "carbon:report:view",
                    "carbon:report:submit",
                    "trade:order:create",
                    "trade:order:view",
                    "trade:transaction:execute",
                    "enterprise:profile:view",
                    "enterprise:profile:update",
                    "admin:user:manage",
                    "admin:system:config"
                );
                redisTemplate.opsForSet().add(cacheKey, permissions.toArray());
                redisTemplate.expire(cacheKey, 6, TimeUnit.HOURS);
                log.debug("预热权限列表缓存完成: {} permissions", permissions.size());
            }
        } catch (Exception e) {
            log.warn("预热权限列表缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 预热碳排放因子缓存
     * Preload emission factor cache
     */
    private void preloadEmissionFactorCache() {
        String cacheKey = "emission_factors:default";
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(cacheKey))) {
                // 默认排放因子 (kgCO2e/unit)
                redisTemplate.opsForHash().put(cacheKey, "electricity_grid", "0.5839"); // kWh
                redisTemplate.opsForHash().put(cacheKey, "natural_gas", "2.1620"); // m³
                redisTemplate.opsForHash().put(cacheKey, "gasoline", "2.9848"); // L
                redisTemplate.opsForHash().put(cacheKey, "diesel", "3.1809"); // L
                redisTemplate.opsForHash().put(cacheKey, "coal", "2.6600"); // kg
                redisTemplate.expire(cacheKey, 24, TimeUnit.HOURS);
                log.debug("预热排放因子缓存完成");
            }
        } catch (Exception e) {
            log.warn("预热排放因子缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 手动刷新所有缓存
     * Manual refresh of all caches
     */
    public void refreshAllCaches() {
        log.info("手动刷新所有缓存");
        
        Collection<String> cacheNames = cacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.debug("清除缓存: {}", cacheName);
            }
        }
        
        // 重新预热（直接调用核心逻辑，不经过@Async代理）
        doPreloadCaches();
    }
    
    /**
     * 获取缓存统计信息
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        int totalCaches = cacheNames.size();
        long totalKeys = 0;
        
        for (String cacheName : cacheNames) {
            Long keyCount = redisTemplate.execute((RedisCallback<Long>) connection -> {
                long count = 0;
                try (var cursor = connection.keyCommands().scan(
                        ScanOptions.scanOptions().match(cacheName + "*").count(100).build())) {
                    while (cursor.hasNext()) {
                        cursor.next();
                        count++;
                    }
                }
                return count;
            });
            totalKeys += keyCount != null ? keyCount : 0L;
        }
        
        return new CacheStatistics(totalCaches, totalKeys);
    }
    
    /**
     * 缓存统计信息
     * Cache Statistics DTO
     */
    public record CacheStatistics(int totalCaches, long totalKeys) {
        public String toSummary() {
            return String.format("缓存统计: %d 个缓存, %d 个键", totalCaches, totalKeys);
        }
    }
}