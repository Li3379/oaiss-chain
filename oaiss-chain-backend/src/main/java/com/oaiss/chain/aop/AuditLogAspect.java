package com.oaiss.chain.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.entity.OperationLog;
import com.oaiss.chain.repository.OperationLogRepository;
import com.oaiss.chain.security.JwtUserDetails;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 操作审计日志切面
 * 
 * <p>拦截标注了@AuditLog注解的方法，记录操作日志</p>
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.oaiss.chain.annotation.AuditLog)")
    public Object recordAuditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        HttpServletRequest request = getCurrentRequest();

        Long userId = null;
        String username = "anonymous";
        Integer userType = 0;
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof JwtUserDetails userDetails) {
                userId = userDetails.getUserId();
                username = userDetails.getUsername();
                userType = userDetails.getUserType();
            }
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        OperationLog operationLog = OperationLog.builder()
                .userId(userId != null ? userId : 0L)
                .username(username)
                .userType(userType)
                .module(annotation.module())
                .action(annotation.action())
                .description(annotation.description())
                .httpMethod(request != null ? request.getMethod() : "UNKNOWN")
                .requestUrl(request != null ? request.getRequestURI() : "UNKNOWN")
                .requestIp(request != null ? getClientIp(request) : "UNKNOWN")
                .userAgent(request != null ? request.getHeader("User-Agent") : "UNKNOWN")
                .build();

        if (annotation.recordParams()) {
            try {
                String params = getRequestParams(joinPoint, annotation.sensitiveFields());
                operationLog.setRequestParams(truncateString(params, 2000));
            } catch (Exception e) {
                log.warn("Failed to record request params", e);
            }
        }

        Object result = null;
        Exception exception = null;

        try {
            result = joinPoint.proceed();
            
            operationLog.setStatus(1);
            
            if (annotation.recordResult() && result != null) {
                try {
                    String resultJson = objectMapper.writeValueAsString(result);
                    operationLog.setResponseResult(truncateString(resultJson, 2000));
                } catch (Exception e) {
                    log.warn("Failed to record response result", e);
                }
            }
        } catch (Exception e) {
            exception = e;
            operationLog.setStatus(2);
            operationLog.setErrorMsg(truncateString(e.getMessage(), 1000));
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            operationLog.setExecutionTime(executionTime);

            // 异步保存日志（避免影响主流程性能）
            try {
                operationLogRepository.save(operationLog);
            } catch (Exception e) {
                log.error("Failed to save operation log", e);
            }
        }

        return result;
    }

    /**
     * 获取当前HTTP请求
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取请求参数
     */
    private String getRequestParams(ProceedingJoinPoint joinPoint, String sensitiveFields) {
        try {
            Set<String> sensitiveSet = new HashSet<>(Arrays.asList(sensitiveFields.split(",")));
            
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return "{}";
            }

            String params = objectMapper.writeValueAsString(args);
            
            // 脱敏处理
            for (String field : sensitiveSet) {
                String fieldPattern = "\"" + field.trim() + "\":\"[^\"]*\"";
                params = params.replaceAll(fieldPattern, "\"" + field.trim() + "\":\"******\"");
            }
            
            return params;
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 截断字符串
     */
    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
