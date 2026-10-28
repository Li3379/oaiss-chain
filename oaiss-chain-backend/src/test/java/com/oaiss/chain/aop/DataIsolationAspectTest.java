package com.oaiss.chain.aop;

import com.oaiss.chain.annotation.DataIsolation;
import com.oaiss.chain.enums.UserTypeEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.EnterpriseContextHolder;
import com.oaiss.chain.security.JwtUserDetails;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 数据隔离切面测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataIsolationAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;
    @Mock
    private jakarta.persistence.EntityManager entityManager;
    @Mock
    private org.hibernate.Session session;

    @InjectMocks
    private DataIsolationAspect dataIsolationAspect;

    @BeforeEach
    void setUp() {
        EnterpriseContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("数据隔离 - 注解禁用时直接放行")
    void handleDataIsolation_Disabled_ShouldProceed() throws Throwable {
        setupMethodMock(false, true);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = dataIsolationAspect.handleDataIsolation(joinPoint);
        assertEquals("result", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("数据隔离 - 企业用户正常访问")
    void handleDataIsolation_EnterpriseUser_ShouldProceed() throws Throwable {
        setupMethodMock(true, true);
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(1L).username("entuser").userType(1)
                .enterpriseId(100L).roles(List.of("ENTERPRISE")).build();
        setSecurityContext(user);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = dataIsolationAspect.handleDataIsolation(joinPoint);
        assertEquals("ok", result);
    }

    @Test
    @DisplayName("数据隔离 - 管理员跳过检查")
    void handleDataIsolation_AdminSkip_ShouldProceed() throws Throwable {
        setupMethodMock(true, true);
        JwtUserDetails admin = JwtUserDetails.builder()
                .userId(2L).username("admin").userType(UserTypeEnum.ADMIN.getCode())
                .roles(List.of("ADMIN")).build();
        setSecurityContext(admin);
        when(joinPoint.proceed()).thenReturn("admin_result");

        Object result = dataIsolationAspect.handleDataIsolation(joinPoint);
        assertEquals("admin_result", result);
    }

    @Test
    @DisplayName("数据隔离 - 未登录抛出异常")
    void handleDataIsolation_NotLoggedIn_ShouldThrow() {
        setupMethodMock(true, true);
        SecurityContextHolder.clearContext();

        assertThrows(BusinessException.class, () -> dataIsolationAspect.handleDataIsolation(joinPoint));
    }

    @Test
    @DisplayName("数据隔离 - 企业用户无企业ID抛出异常")
    void handleDataIsolation_NoEnterpriseId_ShouldThrow() {
        setupMethodMock(true, true);
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(1L).username("entuser").userType(1)
                .enterpriseId(null).roles(List.of("ENTERPRISE")).build();
        setSecurityContext(user);

        assertThrows(BusinessException.class, () -> dataIsolationAspect.handleDataIsolation(joinPoint));
    }

    @Test
    @DisplayName("数据隔离 - skipAdmin=false时管理员不跳过")
    void handleDataIsolation_AdminNotSkip_ShouldProceed() throws Throwable {
        setupMethodMock(true, false);
        JwtUserDetails admin = JwtUserDetails.builder()
                .userId(2L).username("admin").userType(UserTypeEnum.ADMIN.getCode())
                .roles(List.of("ADMIN")).build();
        setSecurityContext(admin);
        when(joinPoint.proceed()).thenReturn("admin_checked");

        Object result = dataIsolationAspect.handleDataIsolation(joinPoint);
        assertEquals("admin_checked", result);
    }

    @Test
    @DisplayName("数据隔离 - 审核员用户正常通过")
    void handleDataIsolation_ReviewerUser_ShouldProceed() throws Throwable {
        setupMethodMock(true, true);
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(3L).username("reviewer").userType(2)
                .roles(List.of("REVIEWER")).build();
        setSecurityContext(user);
        when(joinPoint.proceed()).thenReturn("reviewer_ok");

        Object result = dataIsolationAspect.handleDataIsolation(joinPoint);
        assertEquals("reviewer_ok", result);
    }

    // ==================== Branch Coverage: Unauthenticated non-null auth in getCurrentUser (line 94) ====================

    @Test
    @DisplayName("数据隔离 - 未认证但非null的authentication应抛出异常")
    void handleDataIsolation_UnauthenticatedNonNullAuth_ShouldThrow() {
        setupMethodMock(true, true);
        // 2-arg constructor → authenticated = false
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(BusinessException.class, () -> dataIsolationAspect.handleDataIsolation(joinPoint));
    }

    // ==================== Branch Coverage: Non-JwtUserDetails principal in getCurrentUser (line 96) ====================

    @Test
    @DisplayName("数据隔离 - 非JwtUserDetails的principal应抛出异常")
    void handleDataIsolation_NonJwtUserDetailsPrincipal_ShouldThrow() {
        setupMethodMock(true, true);
        // 3-arg constructor → authenticated = true, but principal is String
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("string_principal", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(BusinessException.class, () -> dataIsolationAspect.handleDataIsolation(joinPoint));
    }

    private void setupMethodMock(boolean enabled, boolean skipAdmin) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        Method method;
        try {
            if (enabled) {
                if (skipAdmin) {
                    method = TestMethods.class.getMethod("enabledWithSkip");
                } else {
                    method = TestMethods.class.getMethod("enabledNoSkip");
                }
            } else {
                method = TestMethods.class.getMethod("disabled");
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        when(methodSignature.getMethod()).thenReturn(method);
    }

    @Test
    @DisplayName("数据隔离 - 企业用户请求透明激活并在finally中关闭Hibernate tenantFilter")
    void handleDataIsolation_EnterpriseUser_ActivatesAndDisablesHibernateFilter() throws Throwable {
        setupMethodMock(true, true);
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(1L).username("entuser").userType(1)
                .enterpriseId(100L).roles(List.of("ENTERPRISE")).build();
        setSecurityContext(user);

        when(entityManager.unwrap(org.hibernate.Session.class)).thenReturn(session);
        org.hibernate.Filter filter = mock(org.hibernate.Filter.class);
        when(session.enableFilter("tenantFilter")).thenReturn(filter);
        when(filter.setParameter("enterpriseId", 100L)).thenReturn(filter);
        when(joinPoint.proceed()).thenReturn("filtered_ok");

        Object result = dataIsolationAspect.handleDataIsolation(joinPoint);

        assertEquals("filtered_ok", result);
        verify(session, times(1)).enableFilter("tenantFilter");
        verify(filter, times(1)).setParameter("enterpriseId", 100L);
        verify(session, times(1)).disableFilter("tenantFilter");
    }

    private void setSecurityContext(JwtUserDetails user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    /** Test method container for @DataIsolation annotation */
    @SuppressWarnings("unused")
    public static class TestMethods {
        @DataIsolation(enabled = false)
        public void disabled() {}

        @DataIsolation(enabled = true, skipAdmin = true)
        public void enabledWithSkip() {}

        @DataIsolation(enabled = true, skipAdmin = false)
        public void enabledNoSkip() {}
    }
}
