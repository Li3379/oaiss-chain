package com.oaiss.chain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 用户会话与令牌黑名单服务
 * 管理用户登出、密码修改后的全局会话失效与 Refresh Token 轮转废止
 *
 * @author OAISS Team
 */
@Slf4j
@Service
public class UserSessionService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String REVOKED_USER_PREFIX = "user:revoked_at:";
    private static final long DEFAULT_REVOKE_TTL_DAYS = 7;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ConcurrentHashMap<String, Long> localBlacklist = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> localRevokedUsers = new ConcurrentHashMap<>();

    public UserSessionService() {
        this.redisTemplate = null;
    }

    public UserSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将特定 Token 加入黑名单
     */
    public void blacklistToken(String token, long ttlMillis) {
        if (token == null || token.isBlank()) {
            return;
        }
        long ttlSeconds = Math.max(1, ttlMillis / 1000);
        try {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    String.valueOf(System.currentTimeMillis()),
                    ttlSeconds,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Redis unavailable for blacklisting token, fallback to local: {}", e.getMessage());
            localBlacklist.put(token, System.currentTimeMillis() + ttlMillis);
        }
    }

    /**
     * 检查特定 Token 是否在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Boolean exists = redisTemplate.hasKey(BLACKLIST_PREFIX + token);
            if (Boolean.TRUE.equals(exists)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for checking token blacklist, fallback to local: {}", e.getMessage());
            Long expireAt = localBlacklist.get(token);
            if (expireAt != null) {
                if (System.currentTimeMillis() < expireAt) {
                    return true;
                }
                localBlacklist.remove(token);
            }
        }
        return false;
    }

    /**
     * 废止指定用户的所有早期活跃会话（用于登出、修改密码、管理员封禁）
     */
    public void revokeUserSessions(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            redisTemplate.opsForValue().set(
                    REVOKED_USER_PREFIX + username,
                    String.valueOf(now),
                    DEFAULT_REVOKE_TTL_DAYS,
                    TimeUnit.DAYS
            );
        } catch (Exception e) {
            log.warn("Redis unavailable for revoking user sessions, fallback to local: {}", e.getMessage());
            localRevokedUsers.put(username, now);
        }
    }

    /**
     * 检查某个 Token 是否在用户会话全局吊销之前签发
     */
    public boolean isSessionRevoked(String username, Date tokenIssuedAt) {
        if (username == null || username.isBlank() || tokenIssuedAt == null) {
            return false;
        }
        Long revokedAt = null;
        try {
            String val = redisTemplate.opsForValue().get(REVOKED_USER_PREFIX + username);
            if (val != null) {
                revokedAt = Long.parseLong(val);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for checking user revoked sessions, fallback to local: {}", e.getMessage());
            revokedAt = localRevokedUsers.get(username);
        }

        if (revokedAt != null) {
            return tokenIssuedAt.getTime() < revokedAt;
        }
        return false;
    }
}
