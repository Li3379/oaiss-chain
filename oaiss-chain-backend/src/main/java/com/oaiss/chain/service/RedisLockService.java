package com.oaiss.chain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁服务
 * Redis Distributed Lock Service
 * 
 * <p>基于 Redis SETNX 实现分布式锁，防止重复提交、并发操作等问题</p>
 * <p>Implements distributed lock using Redis SETNX to prevent duplicate submissions and concurrent operations</p>
 * 
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) "
                            + "else return 0 end",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final java.util.concurrent.ConcurrentHashMap<String, String> localLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 锁前缀
     */
    private static final String LOCK_PREFIX = "oaiss:lock:";

    /**
     * 默认锁过期时间（秒）
     */
    private static final long DEFAULT_EXPIRE_TIME = 30;

    /**
     * 尝试获取分布式锁
     * Try to acquire distributed lock
     * 
     * @param lockKey 锁键
     * @return 锁值（用于释放锁），如果获取失败返回 null
     */
    public String tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_EXPIRE_TIME, TimeUnit.SECONDS);
    }

    /**
     * 尝试获取分布式锁
     * Try to acquire distributed lock
     * 
     * @param lockKey 锁键
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @return 锁值（用于释放锁），如果获取失败返回 null
     */
    public String tryLock(String lockKey, long expireTime, TimeUnit timeUnit) {
        String lockValue = UUID.randomUUID().toString();
        String fullKey = LOCK_PREFIX + lockKey;

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(fullKey, lockValue, expireTime, timeUnit);

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Lock acquired: key={}, value={}", fullKey, lockValue);
                return lockValue;
            }

            log.debug("Lock acquisition failed: key={}", fullKey);
            return null;
        } catch (Exception e) {
            log.warn("Redis unavailable for lock: key={}, falling back to local JVM lock. Reason: {}", fullKey, e.getMessage());
            String existing = localLocks.putIfAbsent(fullKey, lockValue);
            return existing == null ? lockValue : null;
        }
    }

    /**
     * 尝试获取分布式锁（带重试）
     * Try to acquire distributed lock with retry
     * 
     * @param lockKey 锁键
     * @param waitTime 等待时间
     * @param leaseTime 持有时间
     * @param timeUnit 时间单位
     * @return 锁值（用于释放锁），如果获取失败返回 null
     */
    public String tryLockWithRetry(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        return tryLockWithRetry(lockKey, waitTime, timeUnit, leaseTime, timeUnit);
    }

    /**
     * 尝试获取分布式锁（带重试，等待与持有时间单位可独立配置）
     * Try to acquire distributed lock with retry using independent wait and lease units.
     */
    public String tryLockWithRetry(String lockKey,
                                   long waitTime,
                                   TimeUnit waitUnit,
                                   long leaseTime,
                                   TimeUnit leaseUnit) {
        long startTime = System.currentTimeMillis();
        long waitMillis = waitUnit.toMillis(waitTime);

        while (System.currentTimeMillis() - startTime < waitMillis) {
            String lockValue = tryLock(lockKey, leaseTime, leaseUnit);
            if (lockValue != null) {
                return lockValue;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        log.warn("Lock acquisition timeout: key={}", LOCK_PREFIX + lockKey);
        return null;
    }

    /**
     * 释放分布式锁
     * Release distributed lock
     * 
     * @param lockKey 锁键
     * @param lockValue 锁值
     * @return 是否释放成功
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        if (lockValue == null) {
            return false;
        }

        String fullKey = LOCK_PREFIX + lockKey;
        try {
            Long deleted = redisTemplate.execute(
                    COMPARE_AND_DELETE_SCRIPT,
                    Collections.singletonList(fullKey),
                    lockValue
            );
            if (deleted != null && deleted > 0) {
                log.debug("Lock released: key={}", fullKey);
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for releaseLock: key={}, releasing local JVM lock", fullKey);
            return localLocks.remove(fullKey, lockValue);
        }

        if (localLocks.containsKey(fullKey)) {
            return localLocks.remove(fullKey, lockValue);
        }

        log.warn("Lock release failed (value mismatch or already expired): key={}", fullKey);
        return false;
    }

    /**
     * 检查锁是否存在
     * Check if lock exists
     * 
     * @param lockKey 锁键
     * @return 是否存在
     */
    public boolean isLocked(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
        } catch (Exception e) {
            return localLocks.containsKey(fullKey);
        }
    }

    /**
     * 强制释放锁（谨慎使用）
     * Force release lock (use with caution)
     * 
     * @param lockKey 锁键
     * @return 是否释放成功
     */
    public boolean forceReleaseLock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        localLocks.remove(fullKey);
        try {
            Boolean deleted = redisTemplate.delete(fullKey);
            log.warn("Force released lock: key={}", fullKey);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.warn("Force released local lock: key={}", fullKey);
            return true;
        }
    }
}
