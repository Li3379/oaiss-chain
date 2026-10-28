package com.oaiss.chain.aop;

import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.LoginRequest;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;

/**
 * 鎺ュ彛闄愭祦鍒囬潰
 * 
 * <p>鎷︽埅鏍囨敞浜咢RateLimit娉ㄨВ鐨勬柟娉曪紝杩涜闄愭祦鎺у埗</p>
 * <p>鍩轰簬Redis瀹炵幇鍒嗗竷寮忛檺娴?/p>
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@ConditionalOnProperty(name = "oaiss.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class RateLimitAspect {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, Long> redisScriptTemplate;

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    private static final String LUA_INCR_EXPIRE = """
            local current = redis.call('incr', KEYS[1])
            if current == 1 then
                redis.call('expire', KEYS[1], ARGV[1])
            end
            return current
            """;

    @Around("@annotation(com.oaiss.chain.annotation.RateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        // 鑾峰彇娉ㄨВ
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);

        // 鏋勫缓闄愭祦Key
        String key = buildRateLimitKey(annotation, joinPoint);
        
        // 鑾峰彇鏃堕棿绐楀彛鍜岄檺鍒舵鏁?
        int period = annotation.period();
        int limit = annotation.limit();

        try {
            // 浣跨敤Lua鑴氭湰鍘熷瓙鎿嶄綔锛氶€掑+璁剧疆杩囨湡
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_INCR_EXPIRE, Long.class);
            Long currentCount = redisScriptTemplate.execute(script,
                    Collections.singletonList(key), String.valueOf(period));

            // 妫€鏌ユ槸鍚﹁秴杩囬檺鍒?
            if (currentCount != null && currentCount > limit) {
                log.warn("Rate limit exceeded for key: {}, count: {}, limit: {}", key, currentCount, limit);
                throw new BusinessException(ErrorCode.REQUEST_TOO_FREQUENT, annotation.message());
            }

            log.debug("Rate limit check passed for key: {}, count: {}/{}", key, currentCount, limit);
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}", key, e);
            // 闄愭祦妫€鏌ュけ璐ユ椂涓嶉樆姝㈣姹傦紙闄嶇骇澶勭悊锛?
        }

        // 鎵ц鍘熸柟娉?
        return joinPoint.proceed();
    }

    /**
     * 鏋勫缓闄愭祦Key
     */
    private String buildRateLimitKey(RateLimit annotation, ProceedingJoinPoint joinPoint) {
        StringBuilder keyBuilder = new StringBuilder(RATE_LIMIT_KEY_PREFIX);

        // 娣诲姞鑷畾涔塊ey
        String customKey = annotation.key();
        if (customKey != null && !customKey.isEmpty()) {
            keyBuilder.append(customKey).append(":");
        } else {
            // 浣跨敤绫诲悕鍜屾柟娉曞悕浣滀负Key
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();
            keyBuilder.append(className).append(":").append(methodName).append(":");
        }

        // 鏍规嵁闄愭祦绫诲瀷娣诲姞鏍囪瘑
        RateLimit.LimitType limitType = annotation.limitType();
        
        switch (limitType) {
            case IP -> {
                String ip = getClientIp();
                keyBuilder.append("ip:").append(ip);
            }
            case USER -> {
                String userId = resolveRateLimitUserKey(joinPoint);
                keyBuilder.append("user:").append(userId);
            }
            case IP_USER -> {
                String ip = getClientIp();
                String userId = resolveRateLimitUserKey(joinPoint);
                keyBuilder.append("ip:").append(ip).append(":user:").append(userId);
            }
            default -> keyBuilder.append("global");
        }

        return keyBuilder.toString();
    }

    /**
     * 鑾峰彇褰撳墠HTTP璇锋眰
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        return com.oaiss.chain.util.CommonUtils.getClientIp();
    }

    /**
     * 鑾峰彇褰撳墠鐢ㄦ埛ID
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof JwtUserDetails userDetails) {
                return String.valueOf(userDetails.getUserId());
            }
        }
        return "anonymous";
    }

    /**
     * Resolve a stable rate-limit user key for authenticated and pre-auth requests.
     */
    private String resolveRateLimitUserKey(ProceedingJoinPoint joinPoint) {
        String currentUserId = getCurrentUserId();
        if (!"anonymous".equals(currentUserId)) {
            return currentUserId;
        }

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof LoginRequest loginRequest
                    && loginRequest.getUsername() != null
                    && !loginRequest.getUsername().isBlank()) {
                return loginRequest.getUsername().trim();
            }
        }

        return currentUserId;
    }
}
