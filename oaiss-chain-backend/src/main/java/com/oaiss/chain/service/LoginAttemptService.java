package com.oaiss.chain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败尝试与防爆破锁定服务
 * 独立于业务数据库事务，防止登录失败回滚导致计数丢失
 *
 * @author OAISS Team
 */
@Slf4j
@Service
public class LoginAttemptService {

    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ConcurrentHashMap<String, AttemptEntry> localAttempts = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this.redisTemplate = null;
    }

    public LoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查用户是否已被锁定
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean isBlocked(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        try {
            String countStr = redisTemplate.opsForValue().get(LOGIN_FAIL_PREFIX + username);
            if (countStr != null) {
                int count = Integer.parseInt(countStr);
                return count >= MAX_LOGIN_ATTEMPTS;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for checking login attempts, fallback to local: {}", e.getMessage());
            AttemptEntry entry = localAttempts.get(username);
            if (entry != null && System.currentTimeMillis() - entry.timestamp < TimeUnit.MINUTES.toMillis(LOCK_DURATION_MINUTES)) {
                return entry.attempts >= MAX_LOGIN_ATTEMPTS;
            }
        }
        return false;
    }

    /**
     * 记录一次登录失败
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int recordFailedAttempt(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        String key = LOGIN_FAIL_PREFIX + username;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
            }
            int attempts = count != null ? count.intValue() : 1;
            log.warn("Recorded failed login for {}: attempt {}/{}", username, attempts, MAX_LOGIN_ATTEMPTS);
            return attempts;
        } catch (Exception e) {
            log.warn("Redis unavailable for recording login attempt, fallback to local: {}", e.getMessage());
            localAttempts.compute(username, (k, existing) -> {
                long now = System.currentTimeMillis();
                if (existing == null || now - existing.timestamp > TimeUnit.MINUTES.toMillis(LOCK_DURATION_MINUTES)) {
                    return new AttemptEntry(1, now);
                }
                return new AttemptEntry(existing.attempts + 1, now);
            });
            AttemptEntry entry = localAttempts.get(username);
            return entry != null ? entry.attempts : 1;
        }
    }

    /**
     * 清除登录失败计数（登录成功后调用）
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void clearFailedAttempts(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
        } catch (Exception e) {
            log.warn("Redis unavailable for clearing login attempts: {}", e.getMessage());
        }
        localAttempts.remove(username);
    }

    public int getMaxLoginAttempts() {
        return MAX_LOGIN_ATTEMPTS;
    }

    private static class AttemptEntry {
        final int attempts;
        final long timestamp;

        AttemptEntry(int attempts, long timestamp) {
            this.attempts = attempts;
            this.timestamp = timestamp;
        }
    }
}
