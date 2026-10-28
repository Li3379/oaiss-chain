package com.oaiss.chain.aop;

import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.service.RedisLockService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DistributedLockAspect 单元测试
 * Unit tests for DistributedLockAspect
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockAspectTest {

    @Mock
    private RedisLockService redisLockService;

    @InjectMocks
    private DistributedLockAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private Method testMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        // Get a real method for testing
        testMethod = TestService.class.getMethod("testMethod", Long.class, String.class);
    }

    /**
     * Test service class with @DistributedLock annotation
     */
    static class TestService {
        @DistributedLock(key = "test:lock")
        public String testMethod(Long id, String name) {
            return "result";
        }

        @DistributedLock(key = "'user:' + #userId", waitTime = 1000, expireTime = 60, errorMessage = "请勿重复操作")
        public String methodWithSpEL(Long userId) {
            return "spel-result";
        }
    }

    @Nested
    @DisplayName("handleDistributedLock 方法测试")
    class HandleDistributedLockTests {

        @Test
        @DisplayName("锁获取和释放成功 - 正常流程")
        void lockAcquiredAndReleasedSuccessfully() throws Throwable {
            // Arrange
            DistributedLock annotation = testMethod.getAnnotation(DistributedLock.class);
            Object expectedResult = "success";

            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(testMethod);
            when(redisLockService.tryLock(anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn("lock-value-123");
            when(joinPoint.proceed()).thenReturn(expectedResult);
            when(redisLockService.releaseLock(anyString(), anyString())).thenReturn(true);

            // Act
            Object result = aspect.handleDistributedLock(joinPoint);

            // Assert
            assertThat(result).isEqualTo(expectedResult);
            verify(redisLockService).tryLock("test:lock", 30, TimeUnit.SECONDS);
            verify(redisLockService).releaseLock("test:lock", "lock-value-123");
            verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("锁获取失败 - 抛出BusinessException")
        void lockAcquisitionFails_ThrowsBusinessException() throws Throwable {
            // Arrange
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(testMethod);
            when(redisLockService.tryLock(anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> aspect.handleDistributedLock(joinPoint))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_IN_PROGRESS)
                    .hasFieldOrPropertyWithValue("message", "操作正在处理中，请勿重复提交");

            verify(redisLockService).tryLock("test:lock", 30, TimeUnit.SECONDS);
            verify(joinPoint, never()).proceed();
            verify(redisLockService, never()).releaseLock(anyString(), anyString());
        }

        @Test
        @DisplayName("带等待时间 - 使用tryLockWithRetry")
        void withWaitTime_UsesTryLockWithRetry() throws Throwable {
            // Arrange
            Method methodWithWait = TestService.class.getMethod("methodWithSpEL", Long.class);

            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(methodWithWait);
            when(joinPoint.getArgs()).thenReturn(new Object[]{123L});
            when(redisLockService.tryLockWithRetry(anyString(), anyLong(), any(TimeUnit.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn("lock-value-retry");
            when(joinPoint.proceed()).thenReturn("result");
            when(redisLockService.releaseLock(anyString(), anyString())).thenReturn(true);

            // Act
            Object result = aspect.handleDistributedLock(joinPoint);

            // Assert
            assertThat(result).isEqualTo("result");
            verify(redisLockService).tryLockWithRetry(
                    eq("user:123"),
                    eq(1000L),
                    eq(TimeUnit.MILLISECONDS),
                    eq(60L),
                    eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("无等待时间 - 使用tryLock")
        void withoutWaitTime_UsesTryLock() throws Throwable {
            // Arrange
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(testMethod);
            when(redisLockService.tryLock(anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn("lock-value");
            when(joinPoint.proceed()).thenReturn("result");
            when(redisLockService.releaseLock(anyString(), anyString())).thenReturn(true);

            // Act
            aspect.handleDistributedLock(joinPoint);

            // Assert
            verify(redisLockService).tryLock(
                    eq("test:lock"),
                    eq(30L),
                    eq(TimeUnit.SECONDS)
            );
            verify(redisLockService, never()).tryLockWithRetry(
                    anyString(),
                    anyLong(),
                    any(TimeUnit.class),
                    anyLong(),
                    any(TimeUnit.class)
            );
        }

        @Test
        @DisplayName("锁释放失败 - 记录警告日志")
        void lockReleaseFailure_Logged() throws Throwable {
            // Arrange
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(testMethod);
            when(redisLockService.tryLock(anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn("lock-value");
            when(joinPoint.proceed()).thenReturn("result");
            when(redisLockService.releaseLock(anyString(), anyString())).thenReturn(false);

            // Act
            Object result = aspect.handleDistributedLock(joinPoint);

            // Assert - should still return result even if release fails
            assertThat(result).isEqualTo("result");
            verify(redisLockService).releaseLock("test:lock", "lock-value");
        }

        @Test
        @DisplayName("方法执行成功 - 返回原方法结果")
        void methodExecutionSucceeds() throws Throwable {
            // Arrange
            Object expected = new Object();
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(testMethod);
            when(redisLockService.tryLock(anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn("lock-value");
            when(joinPoint.proceed()).thenReturn(expected);
            when(redisLockService.releaseLock(anyString(), anyString())).thenReturn(true);

            // Act
            Object result = aspect.handleDistributedLock(joinPoint);

            // Assert
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("方法抛出异常 - 仍然释放锁")
        void methodException_StillReleasesLock() throws Throwable {
            // Arrange
            RuntimeException expectedException = new RuntimeException("Method failed");
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(testMethod);
            when(redisLockService.tryLock(anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn("lock-value");
            when(joinPoint.proceed()).thenThrow(expectedException);
            when(redisLockService.releaseLock(anyString(), anyString())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> aspect.handleDistributedLock(joinPoint))
                    .isSameAs(expectedException);

            // Verify lock is still released
            verify(redisLockService).releaseLock("test:lock", "lock-value");
        }

        @Test
        @DisplayName("自定义错误消息 - 锁获取失败时使用")
        void customErrorMessage_OnLockFailure() throws Throwable {
            // Arrange
            Method methodWithCustomError = TestService.class.getMethod("methodWithSpEL", Long.class);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(methodWithCustomError);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(redisLockService.tryLockWithRetry(anyString(), anyLong(), any(TimeUnit.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> aspect.handleDistributedLock(joinPoint))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("message", "请勿重复操作");
        }
    }

    @Nested
    @DisplayName("parseLockKey 方法测试")
    class ParseLockKeyTests {

        @Test
        @DisplayName("简单键 - 直接返回")
        void simpleKey_ReturnedDirectly() throws Throwable {
            // Arrange
            String key = "simple-lock-key";
            Method method = TestService.class.getMethod("testMethod", Long.class, String.class);

            // Use reflection to test private method
            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            // Act
            String result = (String) parseMethod.invoke(aspect, key, method, new Object[]{123L, "test"});

            // Assert
            assertThat(result).isEqualTo("simple-lock-key");
        }

        @Test
        @DisplayName("SpEL表达式 - 正确解析")
        void spelExpression_ParsedCorrectly() throws Throwable {
            // Arrange
            String expression = "'user:' + #userId";
            Method method = TestService.class.getMethod("methodWithSpEL", Long.class);

            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            // Act
            String result = (String) parseMethod.invoke(aspect, expression, method, new Object[]{456L});

            // Assert
            assertThat(result).isEqualTo("user:456");
        }

        @Test
        @DisplayName("SpEL表达式 - 多参数")
        void spelExpression_WithMultipleParameters() throws Throwable {
            // Arrange
            String expression = "'report:' + #id + ':name:' + #name";
            Method method = TestService.class.getMethod("testMethod", Long.class, String.class);

            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            // Act
            String result = (String) parseMethod.invoke(aspect, expression, method, new Object[]{789L, "testname"});

            // Assert
            assertThat(result).isEqualTo("report:789:name:testname");
        }

        @Test
        @DisplayName("SpEL表达式 - 引号字符串")
        void spelExpression_WithQuotedString() throws Throwable {
            // Arrange
            String expression = "'prefix:' + #name";
            Method method = TestService.class.getMethod("testMethod", Long.class, String.class);

            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            // Act
            String result = (String) parseMethod.invoke(aspect, expression, method, new Object[]{1L, "abc"});

            // Assert
            assertThat(result).isEqualTo("prefix:abc");
        }

        @Test
        @DisplayName("空参数值 - 返回表达式字符串")
        void nullParameterValue_ReturnsExpression() throws Throwable {
            // Arrange
            String expression = "'user:' + #userId";
            Method method = TestService.class.getMethod("methodWithSpEL", Long.class);

            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            // Act - pass null as argument
            String result = (String) parseMethod.invoke(aspect, expression, method, new Object[]{null});

            // Assert - should handle null gracefully
            assertThat(result).isEqualTo("user:null");
        }

        @Test
        @DisplayName("危险SpEL表达式 - 拒绝解析")
        void dangerousSpelExpression_Rejected() throws Throwable {
            // Arrange
            String expression = "T(java.lang.Runtime).getRuntime().exec('calc')";
            Method method = TestService.class.getMethod("methodWithSpEL", Long.class);

            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            // Act / Assert
            assertThatThrownBy(() -> parseMethod.invoke(aspect, expression, method, new Object[]{1L}))
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("Unsupported distributed lock key expression: " + expression);
        }

        @Test
        @DisplayName("表达式包含括号 - 拒绝解析")
        void expressionWithParentheses_Rejected() throws Throwable {
            String expression = "method('key')";
            Method method = TestService.class.getMethod("methodWithSpEL", Long.class);

            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            assertThatThrownBy(() -> parseMethod.invoke(aspect, expression, method, new Object[]{1L}))
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("表达式包含@符号 - 拒绝解析")
        void expressionWithAtSign_Rejected() throws Throwable {
            String expression = "#var@special";
            Method method = TestService.class.getMethod("methodWithSpEL", Long.class);

            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            assertThatThrownBy(() -> parseMethod.invoke(aspect, expression, method, new Object[]{1L}))
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("集成场景测试")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("完整流程 - SpEL键解析和锁操作")
        void fullFlow_SpELKeyAndLockOperations() throws Throwable {
            // Arrange
            Method method = TestService.class.getMethod("methodWithSpEL", Long.class);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(method);
            when(joinPoint.getArgs()).thenReturn(new Object[]{999L});
            when(redisLockService.tryLockWithRetry(
                    eq("user:999"),
                    eq(1000L),
                    eq(TimeUnit.MILLISECONDS),
                    eq(60L),
                    eq(TimeUnit.SECONDS)
            )).thenReturn("lock-uuid");
            when(joinPoint.proceed()).thenReturn("processed");
            when(redisLockService.releaseLock(eq("user:999"), eq("lock-uuid"))).thenReturn(true);

            // Act
            Object result = aspect.handleDistributedLock(joinPoint);

            // Assert
            assertThat(result).isEqualTo("processed");
            verify(redisLockService).tryLockWithRetry("user:999", 1000L, TimeUnit.MILLISECONDS, 60L, TimeUnit.SECONDS);
            verify(redisLockService).releaseLock("user:999", "lock-uuid");
        }

        @Test
        @DisplayName("并发场景 - 多次调用不同方法")
        void concurrentCalls_DifferentMethods() throws Throwable {
            // Arrange - first method
            Method method1 = TestService.class.getMethod("testMethod", Long.class, String.class);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(method1);
            when(redisLockService.tryLock(eq("test:lock"), anyLong(), any(TimeUnit.class)))
                    .thenReturn("lock1");
            when(joinPoint.proceed()).thenReturn("result1");
            when(redisLockService.releaseLock(anyString(), anyString())).thenReturn(true);

            // Act
            aspect.handleDistributedLock(joinPoint);

            // Verify first call
            verify(redisLockService).tryLock("test:lock", 30, TimeUnit.SECONDS);
            verify(redisLockService).releaseLock("test:lock", "lock1");
        }

        @Test
        @DisplayName("带等待时间锁获取失败 - 抛出BusinessException")
        void withWaitTime_lockFails_ThrowsBusinessException() throws Throwable {
            // Arrange
            Method methodWithWait = TestService.class.getMethod("methodWithSpEL", Long.class);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(methodWithWait);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(redisLockService.tryLockWithRetry(anyString(), anyLong(), any(TimeUnit.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> aspect.handleDistributedLock(joinPoint))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("message", "请勿重复操作");

            verify(joinPoint, never()).proceed();
        }

        @Test
        @DisplayName("方法执行异常后锁释放失败 - 仍然返回异常")
        void methodException_lockReleaseFails_stillThrowsOriginalException() throws Throwable {
            RuntimeException expectedException = new RuntimeException("Method failed");
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(testMethod);
            when(redisLockService.tryLock(anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn("lock-value");
            when(joinPoint.proceed()).thenThrow(expectedException);
            when(redisLockService.releaseLock(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> aspect.handleDistributedLock(joinPoint))
                    .isSameAs(expectedException);

            verify(redisLockService).releaseLock("test:lock", "lock-value");
        }

        @Test
        @DisplayName("SpEL表达式结果为null时返回表达式字符串")
        void spelExpression_resolvesToNull_returnsExpressionString() throws Throwable {
            java.lang.reflect.Method parseMethod = DistributedLockAspect.class.getDeclaredMethod(
                    "parseLockKey", String.class, Method.class, Object[].class
            );
            parseMethod.setAccessible(true);

            // '#nonexistent' references an undefined variable → SpEL returns null
            String expression = "#nonexistent";
            Method method = TestService.class.getMethod("methodWithSpEL", Long.class);

            String result = (String) parseMethod.invoke(aspect, expression, method, new Object[]{1L});

            assertThat(result).isEqualTo("#nonexistent");
        }
    }
}
