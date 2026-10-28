package com.oaiss.chain.aop;

import com.oaiss.chain.annotation.RequirePermission;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.AccountPermissionList;
import com.oaiss.chain.entity.EntryPermission;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.AccountPermissionListRepository;
import com.oaiss.chain.repository.EntryPermissionRepository;
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
import java.util.Optional;

/**
 * 权限校验切面
 * 
 * <p>拦截标注了@RequirePermission注解的方法，进行权限校验</p>
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final EntryPermissionRepository entryPermissionRepository;
    private final AccountPermissionListRepository accountPermissionListRepository;

    /**
     * 用户类型常量
     */
    private static final int USER_TYPE_ENTERPRISE = 1;
    private static final int USER_TYPE_REVIEWER = 2;
    private static final int USER_TYPE_THIRD_PARTY = 3;
    private static final int USER_TYPE_ADMIN = 99;

    @Around("@annotation(com.oaiss.chain.annotation.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN, "用户未登录");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserDetails userDetails)) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN, "无效的用户凭证");
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        Integer userType = userDetails.getUserType();
        String errorMessage = annotation.message();

        if (annotation.adminOnly() && userType != USER_TYPE_ADMIN) {
            log.warn("Admin-only access denied for user: {}", userDetails.getUsername());
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, errorMessage);
        }

        if (annotation.enterpriseOnly() && userType != USER_TYPE_ENTERPRISE) {
            log.warn("Enterprise-only access denied for user: {}", userDetails.getUsername());
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, errorMessage);
        }

        if (annotation.reviewerOnly() && userType != USER_TYPE_REVIEWER) {
            log.warn("Reviewer-only access denied for user: {}", userDetails.getUsername());
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, errorMessage);
        }

        if (annotation.thirdPartyOnly() && userType != USER_TYPE_THIRD_PARTY) {
            log.warn("Third-party-only access denied for user: {}", userDetails.getUsername());
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, errorMessage);
        }

        // 如果指定了具体权限编码
        String permissionCode = annotation.value();
        if (!permissionCode.isEmpty()) {
            log.debug("Checking permission code: {} for user: {}", permissionCode, userDetails.getUsername());

            // 管理员拥有所有权限编码
            if (userType != USER_TYPE_ADMIN) {
                AccountPermissionList permission = accountPermissionListRepository
                        .findByPermissionCodeAndDeletedFalse(permissionCode)
                        .orElse(null);

                if (permission == null) {
                    log.warn("Unknown permission code: {} for user: {}", permissionCode, userDetails.getUsername());
                    throw new BusinessException(ErrorCode.PERMISSION_DENIED, "未知的权限编码: " + permissionCode);
                }
            }
        }

        // 基于API路径的权限校验
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            String requestPath = request.getRequestURI();
            String httpMethod = request.getMethod();

            // 移除context-path
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
                requestPath = requestPath.substring(contextPath.length());
            }

            Optional<EntryPermission> permissionOpt = entryPermissionRepository
                    .findByApiPathAndHttpMethodAndDeletedFalse(requestPath, httpMethod);

            if (permissionOpt.isPresent()) {
                EntryPermission permission = permissionOpt.get();
                
                if (!permission.getAllowed()) {
                    log.warn("API access denied for path: {}, user: {}", requestPath, userDetails.getUsername());
                    throw new BusinessException(ErrorCode.PERMISSION_DENIED, errorMessage);
                }

                // 检查用户类型是否匹配
                if (permission.getUserType() != null && permission.getUserType() != 0) {
                    if (!permission.getUserType().equals(userType) && userType != USER_TYPE_ADMIN) {
                        log.warn("User type mismatch for path: {}, required: {}, actual: {}", 
                                requestPath, permission.getUserType(), userType);
                        throw new BusinessException(ErrorCode.PERMISSION_DENIED, errorMessage);
                    }
                }
            }
        }

        return joinPoint.proceed();
    }

    /**
     * 获取当前HTTP请求
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
