package com.oaiss.chain.aop;

import com.oaiss.chain.annotation.DataIsolation;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.enums.UserTypeEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.EnterpriseContextHolder;
import com.oaiss.chain.security.JwtUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 数据隔离切面
 * Data Isolation Aspect
 * 
 * <p>拦截标注了@DataIsolation注解的方法，设置企业上下文，并透明激活底层 Hibernate 租户行级过滤器</p>
 * 
 * @author OAISS Team
 */
@Slf4j
@Aspect
@Component
public class DataIsolationAspect {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 用户类型常量
     */
    private static final int USER_TYPE_ADMIN = UserTypeEnum.ADMIN.getCode();
    private static final int USER_TYPE_ENTERPRISE = UserTypeEnum.ENTERPRISE.getCode();

    /**
     * 环绕通知：数据隔离处理
     */
    @Around("@annotation(com.oaiss.chain.annotation.DataIsolation)")
    public Object handleDataIsolation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DataIsolation annotation = method.getAnnotation(DataIsolation.class);

        if (!annotation.enabled()) {
            return joinPoint.proceed();
        }

        Session session = null;
        try {
            // 1. 获取当前用户并初始化上下文
            JwtUserDetails userDetails = getCurrentUser();
            if (userDetails == null) {
                log.warn("Data isolation: No authenticated user found");
                throw new BusinessException(ErrorCode.USER_NOT_LOGIN, "用户未登录");
            }

            // 2. 初始化企业上下文
            EnterpriseContextHolder.initFromJwtUserDetails(userDetails);

            // 3. 检查是否跳过管理员
            if (annotation.skipAdmin() && userDetails.getUserType() == USER_TYPE_ADMIN) {
                log.debug("Data isolation: Skipping admin user {}", userDetails.getUsername());
                return joinPoint.proceed();
            }

            // 4. 验证企业用户是否有企业ID，并透明激活 Hibernate 行级租户过滤器
            if (userDetails.getUserType() == USER_TYPE_ENTERPRISE) {
                if (userDetails.getEnterpriseId() == null) {
                    log.warn("Data isolation: Enterprise user {} has no enterprise ID", userDetails.getUsername());
                    throw new BusinessException(ErrorCode.PERMISSION_DENIED, "当前用户未关联企业");
                }
                try {
                    if (entityManager != null) {
                        session = entityManager.unwrap(Session.class);
                        if (session != null) {
                            session.enableFilter("tenantFilter")
                                   .setParameter("enterpriseId", userDetails.getEnterpriseId());
                            log.debug("Data isolation: Activated Hibernate tenantFilter for enterpriseId={}", userDetails.getEnterpriseId());
                        }
                    }
                } catch (Exception e) {
                    log.trace("Hibernate session unwrap skipped: {}", e.getMessage());
                }
            }

            // 5. 执行原方法
            return joinPoint.proceed();

        } finally {
            // 6. 停用过滤器并清除上下文
            if (session != null) {
                try {
                    session.disableFilter("tenantFilter");
                } catch (Exception ignored) {
                }
            }
            EnterpriseContextHolder.clear();
        }
    }

    /**
     * 获取当前登录用户
     */
    private JwtUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof JwtUserDetails) {
                return (JwtUserDetails) principal;
            }
        }
        return null;
    }
}
