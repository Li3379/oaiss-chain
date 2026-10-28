package com.oaiss.chain.aop;

import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.LoginRequest;
import com.oaiss.chain.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisTemplate<String, Long> redisScriptTemplate;

    private RateLimitAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private Method testMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        aspect = new RateLimitAspect(redisTemplate, redisScriptTemplate);
        testMethod = TestService.class.getMethod("defaultLimitMethod");
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testRateLimitCheckPassed_UnderLimit() throws Throwable {
        Object expectedResult = "success";
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(testMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("defaultLimitMethod");
        when(redisScriptTemplate.execute(
                any(DefaultRedisScript.class),
                any(),
                anyString()
        )).thenReturn(50L);
        when(joinPoint.proceed()).thenReturn(expectedResult);

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo(expectedResult);
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                any(),
                anyString()
        );
        verify(joinPoint).proceed();
    }

    @Test
    void testRateLimitCheckFailed_OverLimit_ThrowsBusinessException() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(testMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("defaultLimitMethod");
        when(redisScriptTemplate.execute(
                any(DefaultRedisScript.class),
                any(),
                anyString()
        )).thenReturn(101L); // Over default limit (100)

        assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.REQUEST_TOO_FREQUENT);

        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                any(),
                anyString()
        );
        verify(joinPoint, never()).proceed();
    }

    @Test
    void testIpUserRateLimitUsesUsernameForAnonymousLoginRequests() throws Throwable {
        Method loginMethod = TestService.class.getMethod("login", LoginRequest.class);
        LoginRequest loginRequest = LoginRequest.builder()
                .username("enterprise001")
                .password("admin123")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(loginMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{loginRequest});
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:auth:login:ip:10.0.0.8:user:enterprise001")),
                eq("60")
        );
    }

    // ==================== Branch Coverage: LimitType.IP ====================

    @Test
    void testIpRateLimit_UsesClientIp() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:192.168.1.1")),
                eq("30")
        );
    }

    // ==================== Branch Coverage: LimitType.USER ====================

    @Test
    void testUserRateLimit_AuthenticatedUser_UsesUserId() throws Throwable {
        Method userMethod = TestService.class.getMethod("userLimitMethod");

        // Set up authenticated user in SecurityContext
        com.oaiss.chain.security.JwtUserDetails userDetails =
                com.oaiss.chain.security.JwtUserDetails.builder()
                        .userId(42L)
                        .username("testuser")
                        .roles(List.of("ENTERPRISE"))
                        .build();
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(userMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("userLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:userLimitMethod:user:42")),
                eq("60")
        );

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ==================== Branch Coverage: Redis Error Degradation ====================

    @Test
    void testRedisError_DoesNotBlockRequest() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(testMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("defaultLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString()))
                .thenThrow(new RuntimeException("Redis connection failed"));
        when(joinPoint.proceed()).thenReturn("degraded_result");

        Object result = aspect.enforceRateLimit(joinPoint);

        // Request should still proceed when Redis fails (degraded mode)
        assertThat(result).isEqualTo("degraded_result");
        verify(joinPoint).proceed();
    }

    // ==================== Branch Coverage: Null Count from Redis ====================

    @Test
    void testNullCountFromRedis_ShouldProceed() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(testMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("defaultLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString()))
                .thenReturn(null);
        when(joinPoint.proceed()).thenReturn("null_count_result");

        Object result = aspect.enforceRateLimit(joinPoint);

        // null count should NOT trigger rate limit (currentCount != null check fails)
        assertThat(result).isEqualTo("null_count_result");
        verify(joinPoint).proceed();
    }

    // ==================== Branch Coverage: LoginRequest with blank username ====================

    @Test
    void testIpUserRateLimit_LoginRequestBlankUsername_FallsBackToAnonymous() throws Throwable {
        Method loginMethod = TestService.class.getMethod("login", LoginRequest.class);
        LoginRequest loginRequest = LoginRequest.builder()
                .username("   ")  // blank username
                .password("admin123")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(loginMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{loginRequest});
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        // Since username is blank, should fall back to "anonymous"
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:auth:login:ip:10.0.0.5:user:anonymous")),
                eq("60")
        );
    }

    // ==================== Branch Coverage: X-Forwarded-For Header ====================

    @Test
    void testIpRateLimit_UsesXForwardedFor() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:203.0.113.50")),
                eq("30")
        );
    }

    // ==================== Branch Coverage: X-Real-IP Header ====================

    @Test
    void testIpRateLimit_UsesXRealIp() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.25");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:198.51.100.25")),
                eq("30")
        );
    }

    // ==================== Branch Coverage: Comma-separated X-Forwarded-For ====================

    @Test
    void testIpRateLimit_CommaSeparatedXForwardedFor_UsesFirst() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18, 150.172.238.178");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:203.0.113.1")),
                eq("30")
        );
    }

    // ==================== Branch Coverage: No HTTP Request ====================

    @Test
    void testIpRateLimit_NoHttpRequest_IpIsUnknown() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        // Do NOT set RequestContextHolder - simulates no HTTP request context

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:unknown")),
                eq("30")
        );
    }

    // ==================== Branch Coverage: LoginRequest null username ====================

    @Test
    void testIpUserRateLimit_LoginRequestNullUsername_FallsBackToAnonymous() throws Throwable {
        Method loginMethod = TestService.class.getMethod("login", LoginRequest.class);
        LoginRequest loginRequest = LoginRequest.builder()
                .username(null)
                .password("admin123")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(loginMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{loginRequest});
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:auth:login:ip:10.0.0.9:user:anonymous")),
                eq("60")
        );
    }

    // ==================== Branch Coverage: Default LimitType ====================

    @Test
    void testDefaultLimitType_UsesGlobal() throws Throwable {
        Method defaultMethod = TestService.class.getMethod("defaultLimitMethod");

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(defaultMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("defaultLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("global_result");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("global_result");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:defaultLimitMethod:global")),
                eq("60")
        );
    }

    // ==================== Branch Coverage: Count Exactly At Limit ====================

    @Test
    void testRateLimitCheckPassed_ExactlyAtLimit_ShouldProceed() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(testMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("defaultLimitMethod");
        when(redisScriptTemplate.execute(
                any(DefaultRedisScript.class),
                any(),
                anyString()
        )).thenReturn(100L); // Exactly at limit (not over)
        when(joinPoint.proceed()).thenReturn("at_limit_result");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("at_limit_result");
        verify(joinPoint).proceed();
    }

    // ==================== Branch Coverage: Non-LoginRequest Arguments ====================

    @Test
    void testIpUserRateLimit_NonLoginRequestArg_FallsBackToAnonymous() throws Throwable {
        Method loginMethod = TestService.class.getMethod("login", LoginRequest.class);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(loginMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"not-a-login-request"});
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        // Non-LoginRequest arg → falls through to anonymous
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:auth:login:ip:10.0.0.7:user:anonymous")),
                eq("60")
        );
    }

    // ==================== Branch Coverage: Non-JwtUserDetails Authentication ====================

    @Test
    void testUserRateLimit_NonJwtUserDetailsAuth_UsesAnonymous() throws Throwable {
        Method userMethod = TestService.class.getMethod("userLimitMethod");

        // Set authentication with a non-JwtUserDetails principal
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "string_principal", null, List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(userMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("userLimitMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:userLimitMethod:user:anonymous")),
                eq("60")
        );

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ==================== Branch Coverage: Empty Custom Key ====================

    @Test
    void testDefaultLimitType_EmptyCustomKey_UsesClassNameAndMethod() throws Throwable {
        Method defaultMethod = TestService.class.getMethod("defaultLimitMethod");

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(defaultMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("defaultLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        // Default annotation has key="" → falls to else branch using className:methodName
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:defaultLimitMethod:global")),
                eq("60")
        );
    }

    @Test
    void testIpRateLimit_XForwardedForUnknown_FallsToXRealIp() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "198.51.100.50");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:198.51.100.50")),
                eq("30")
        );
    }

    @Test
    void testIpRateLimit_EmptyXForwardedFor_FallsToXRealIp() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "");
        request.addHeader("X-Real-IP", "198.51.100.51");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:198.51.100.51")),
                eq("30")
        );
    }

    @Test
    void testUserRateLimit_NullAuthentication_UsesAnonymous() throws Throwable {
        Method userMethod = TestService.class.getMethod("userLimitMethod");

        // Clear authentication context
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(userMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("userLimitMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:userLimitMethod:user:anonymous")),
                eq("60")
        );
    }

    // ==================== Branch Coverage: X-Real-IP empty after invalid X-Forwarded-For ====================

    @Test
    void testIpRateLimit_EmptyXRealIpAfterInvalidForwardedFor_FallsToRemoteAddr() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.99");
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:10.0.0.99")),
                eq("30")
        );
    }

    // ==================== Branch Coverage: X-Real-IP "unknown" after invalid X-Forwarded-For ====================

    @Test
    void testIpRateLimit_UnknownXRealIpAfterInvalidForwardedFor_FallsToRemoteAddr() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.88");
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "unknown");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:10.0.0.88")),
                eq("30")
        );
    }

    // ==================== Branch Coverage: Null remoteAddr → ip null → comma check & ternary ====================

    @Test
    void testIpRateLimit_NullRemoteAddr_ReturnsUnknown() throws Throwable {
        Method ipMethod = TestService.class.getMethod("ipLimitMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ipMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("ipLimitMethod");
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        // ip is null → comma check skipped, ternary returns "unknown"
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:ipLimitMethod:ip:unknown")),
                eq("30")
        );
    }

    // ==================== Branch Coverage: Unauthenticated non-null auth in getCurrentUserId ====================

    @Test
    void testUserRateLimit_UnauthenticatedNonNullAuth_UsesAnonymous() throws Throwable {
        Method userMethod = TestService.class.getMethod("userLimitMethod");

        // 2-arg constructor creates an unauthenticated token
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "user", null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(userMethod);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(signature.getName()).thenReturn("userLimitMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(redisScriptTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforceRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        // isAuthenticated=false → getCurrentUserId returns "anonymous"
        verify(redisScriptTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate_limit:TestService:userLimitMethod:user:anonymous")),
                eq("60")
        );

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    static class TestService {
        @RateLimit
        public String defaultLimitMethod() {
            return "result";
        }

        @RateLimit(key = "auth:login", limit = 5, period = 60, limitType = RateLimit.LimitType.IP_USER)
        public String login(LoginRequest request) {
            return "login";
        }

        @RateLimit(limitType = RateLimit.LimitType.IP, period = 30, limit = 50)
        public String ipLimitMethod() {
            return "ip_result";
        }

        @RateLimit(limitType = RateLimit.LimitType.USER, period = 60, limit = 100)
        public String userLimitMethod() {
            return "user_result";
        }
    }
}
